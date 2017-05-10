package futurethrottle.monix
import monix.eval.Task

class ThrottleImpl extends Throttle {
  override def throttle[A](task: Task[A]): Task[A] = {
    // TODO
    Task.deferAction { scheduler =>
      task
    }
  }
}
