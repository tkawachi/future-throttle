package futurethrottle

import scala.concurrent.Future

trait Throttle {
  def throttle[A](f: => Future[A]): Future[A]
}
