import java.time.Clock
import java.util.{Timer, TimerTask}

import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global // TODO

trait Throttle {
  def throttle[A](f: => Future[A]): Future[A]
}

case class ThrottleRate(n: Int, span: FiniteDuration)

class ThrottleImpl(rate: ThrottleRate, clock: Clock, timer: Timer) extends Throttle {
  private val finishedTimes = new mutable.Queue[Long]
  private var inFlight = 0
  private val pendingJobs = new mutable.Queue[Runnable]

  override def throttle[A](f: => Future[A]): Future[A] = synchronized {
    val now = clock.millis()
    removePastTimes(now)
    if (finishedTimes.length + inFlight < rate.n) {
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

      scheduleStart(now)

      promise.future
    }
  }

  private def scheduleStart[A](now: Long) = synchronized {
    timer.schedule(new TimerTask {
      override def run(): Unit = startNextPendingJobs()
    }, finishedTimes.headOption.map(_ + rate.span.toMillis - now).getOrElse(0L))
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
    while (pendingJobs.nonEmpty && finishedTimes.length + inFlight < rate.n) {
      pendingJobs.dequeue().run()
    }
    if (pendingJobs.nonEmpty) {
      scheduleStart(now)
    }
  }
}
