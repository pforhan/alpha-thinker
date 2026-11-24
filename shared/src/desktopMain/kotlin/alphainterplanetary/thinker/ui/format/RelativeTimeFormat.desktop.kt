package alphainterplanetary.thinker.ui.format

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

actual fun formatAbsoluteDate(instant: Instant, style: AbsoluteDateStyle): String {
  val pattern = when (style) {
    AbsoluteDateStyle.MonthDay -> "MMM d"
    AbsoluteDateStyle.MonthYear -> "MMM yyyy"
    AbsoluteDateStyle.YearOnly -> "yyyy"
  }
  return DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    .withZone(ZoneId.systemDefault())
    .format(instant.toJavaInstant())
}
