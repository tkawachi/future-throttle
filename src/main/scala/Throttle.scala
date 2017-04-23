import java.time.Clock
import java.util.{Timer, TimerTask}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global // TODO

trait Throttle {
  def throttle[A](f: => Future[A]): Future[A]
}

case class ThrottleRate(n: Int, span: FiniteDuration) {
  require(span.toMillis > 0,
          "span must be equal or greater than 1 milli second")
  require(n > 0, "n must be greater than 0")
}

class ThrottleImpl(rate: ThrottleRate, clock: Clock, timer: Timer)
    extends Throttle {
  private val finishedTimes = new mutable.Queue[Long]
  private var inFlight = 0
  private val pendingJobs = new mutable.Queue[Runnable]
  private var isScheduled = false

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

  private def canStart = synchronized {
    finishedTimes.length + inFlight < rate.n
  }

  private def scheduleStart[A](now: Long) = synchronized {
    finishedTimes.headOption.foreach { oldestFinishedTime =>
      val scheduleTime = oldestFinishedTime + rate.span.toMillis
      timer.schedule(new TimerTask {
        override def run(): Unit = onScheduledTask()
      }, scheduleTime - now)
      isScheduled = true
    }
  }

  private def onScheduledTask(): Unit = synchronized {
    isScheduled = false
    startNextPendingJobs()
  }

  private def startFuture[A](f: => Future[A]): Future[A] = synchronized {
    inFlight += 1

    val result = f
    result.onComplete(_ => handleCompletion())
    result
  }

  private def removePastTimes(now: Long): Unit = synchronized {
    val pastPoint = now - rate.span.toMillis
    while (finishedTimes.dequeueFirst(_ < pastPoint).isDefined) {}
  }

  private def handleCompletion(): Unit = synchronized {
    inFlight -= 1
    finishedTimes.enqueue(clock.millis())
    startNextPendingJobs()
  }

  private def startNextPendingJobs(): Unit = synchronized {
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
