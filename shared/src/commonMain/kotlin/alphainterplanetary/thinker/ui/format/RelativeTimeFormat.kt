package alphainterplanetary.thinker.ui.format

import kotlin.time.Instant

private const val SECONDS_PER_DAY = 86_400L

enum class AbsoluteDateStyle {
  MonthDay,
  MonthYear,
  YearOnly,
}

/**
 * Formats [instant] relative to [now] for revision/history display.
 *
 * Uses seconds/minutes/hours ago under 1 day, x days ago under 7 days, platform-preferred
 * short month + day under 90 days, short month + year under 18 months, and year only
 * for older timestamps.
 *
 * [absoluteFormatter] defaults to the platform expect/actual; tests can pass a deterministic stub.
 */
fun formatRelativeTime(
  instant: Instant,
  now: Instant,
  absoluteFormatter: (Instant, AbsoluteDateStyle) -> String = ::formatAbsoluteDate,
): String {
  val delta = now - instant
  val seconds = delta.inWholeSeconds.coerceAtLeast(0)
  val minutes = delta.inWholeMinutes.coerceAtLeast(0)
  val hours = delta.inWholeHours.coerceAtLeast(0)
  val days = delta.inWholeDays.coerceAtLeast(0)

  return when {
    // Over 18 months, year only. 18 months is close to 547.5 but we just went with 545.
    days > 545 -> absoluteFormatter(instant, AbsoluteDateStyle.YearOnly)
    // 3-18 months, month and year.
    days > 90 -> absoluteFormatter(instant, AbsoluteDateStyle.MonthYear)
    days > 7 -> absoluteFormatter(instant, AbsoluteDateStyle.MonthDay)
    days > 0 -> "${days}d ago"
    hours > 0 -> "${hours}h ago"
    minutes > 0 -> "${minutes}m ago"
    else -> "${seconds}s ago"
  }
}

expect fun formatAbsoluteDate(instant: Instant, style: AbsoluteDateStyle): String
