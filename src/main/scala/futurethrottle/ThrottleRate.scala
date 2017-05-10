package futurethrottle

import scala.concurrent.duration.FiniteDuration

case class ThrottleRate(n: Int, span: FiniteDuration) {
  require(span.toMillis > 0,
          "span must be equal or greater than 1 milli second")
  require(n > 0, "n must be greater than 0")
}
