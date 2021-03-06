package futurethrottle

import java.time.Clock
import java.util.{Timer, TimerTask}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.global
import scala.concurrent.{Future, Promise}

class ThrottleImpl(rate: ThrottleRate, clock: Clock, timer: Timer)
    extends Throttle {

  private[this] val finishedTimes = new mutable.Queue[Long]
  private[this] var inFlight = 0
  private[this] val pendingJobs = new mutable.Queue[Runnable]
  // pendingJobs から取り出して実行するスケジュールは1つだけで十分
  private[this] var isScheduled = false

  override def throttle[A](f: => Future[A]): Future[A] = synchronized {
    val now = clock.millis()
    removePastTimes(now)
    if (canStart) {
      // run now
      startFuture(f)
    } else {
      // schedule later
      val promise = Promise[A]
      val runnable = new Runnable {
        override def run(): Unit = {
          val result = startFuture(f)
          promise.completeWith(result)
        }
      }
      pendingJobs.enqueue(runnable)
      if (!isScheduled) {
        scheduleStart(now)
      }
      promise.future
    }
  }

  private[this] def canStart = synchronized {
    finishedTimes.length + inFlight < rate.n
  }

  private[this] def scheduleStart[A](now: Long) = synchronized {
    finishedTimes.headOption.foreach { oldestFinishedTime =>
      val scheduleTime = oldestFinishedTime + rate.span.toMillis
      timer.schedule(new TimerTask {
        override def run(): Unit = onScheduledTask()
      }, scheduleTime - now)
      isScheduled = true
    }
  }

  private[this] def onScheduledTask(): Unit = synchronized {
    isScheduled = false
    startNextPendingJobs()
  }

  private[this] def startFuture[A](f: => Future[A]): Future[A] = synchronized {
    inFlight += 1

    val result = f
    result.onComplete(_ => handleCompletion())(global)
    result
  }

  private[this] def removePastTimes(now: Long): Unit = synchronized {
    val pastPoint = now - rate.span.toMillis
    while (finishedTimes.dequeueFirst(_ < pastPoint).isDefined) {}
  }

  private[this] def handleCompletion(): Unit = synchronized {
    inFlight -= 1
    finishedTimes.enqueue(clock.millis())
    startNextPendingJobs()
  }

  private[this] def startNextPendingJobs(): Unit = synchronized {
    val now = clock.millis()
    removePastTimes(now)
    while (pendingJobs.nonEmpty && canStart) {
      pendingJobs.dequeue().run()
    }
    if (pendingJobs.nonEmpty && !isScheduled) {
      scheduleStart(now)
    }
  }
}
