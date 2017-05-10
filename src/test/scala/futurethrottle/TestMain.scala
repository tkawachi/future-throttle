package futurethrottle

import java.time.Clock
import java.util.Timer
import java.util.concurrent.{Executors, ThreadFactory}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Random

object TestMain extends App {
  implicit val ec = ExecutionContext.fromExecutorService(
    Executors.newCachedThreadPool(new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        t.setDaemon(true)
        t
      }
    }))
  val throttle = new ThrottleImpl(ThrottleRate(10, 400.millis),
                                  Clock.systemDefaultZone(),
                                  new Timer(true))

  val startT = System.currentTimeMillis()

  val f = Future.traverse((0 until 300).toList) { i =>
    throttle.throttle(Future {
      Thread.sleep(Random.nextInt(100))
      println((i, Thread.currentThread().getName))
    })
  }

  Await.result(f, Duration.Inf)
  val endT = System.currentTimeMillis()

  println(s"finish ${endT - startT}")
}
