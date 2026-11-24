package alphainterplanetary.thinker.ui.format

import kotlin.time.Instant
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

actual fun formatAbsoluteDate(instant: Instant, style: AbsoluteDateStyle): String {
  val template = when (style) {
    AbsoluteDateStyle.MonthDay -> "MMMd"
    AbsoluteDateStyle.MonthYear -> "MMMy"
    AbsoluteDateStyle.YearOnly -> "yyyy"
  }
  val formatter = NSDateFormatter().apply {
    locale = NSLocale.currentLocale
    setLocalizedDateFormatFromTemplate(template)
  }
  val date = NSDate.dateWithTimeIntervalSince1970(instant.toEpochMilliseconds() / 1000.0)
  return formatter.stringFromDate(date)
}
