package futurethrottle.monix

import monix.eval.Task

trait Throttle {
  def throttle[A](task: Task[A]): Task[A]
}
