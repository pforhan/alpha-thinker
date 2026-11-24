package alphainterplanetary.thinker.ui.format

import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Instant

actual fun formatAbsoluteDate(instant: Instant, style: AbsoluteDateStyle): String {
  val skeleton = when (style) {
    AbsoluteDateStyle.MonthDay -> "MMMd"
    AbsoluteDateStyle.MonthYear -> "MMMy"
    AbsoluteDateStyle.YearOnly -> "yyyy"
  }
  val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton)
  val format = SimpleDateFormat(pattern, Locale.getDefault()).apply {
    timeZone = TimeZone.getDefault()
  }
  return format.format(Date(instant.toEpochMilliseconds()))
}
