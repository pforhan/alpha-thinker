package alphainterplanetary.thinker.util

import kotlin.time.Duration

/**
 * Formats a duration for the phase timeline, e.g. `"1d 3h"`, `"4h 20m"`,
 * `"42m"`, or `"<1m"` for anything under a minute.
 */
fun formatDuration(duration: Duration): String {
  val minutes = duration.inWholeMinutes.coerceAtLeast(0)
  if (minutes < 1) return "<1m"
  val hours = minutes / 60
  val remMinutes = minutes % 60
  val days = hours / 24
  val remHours = hours % 24
  return when {
    days > 0 && remHours > 0 -> "${days}d ${remHours}h"
    days > 0 -> "${days}d"
    hours > 0 && remMinutes > 0 -> "${hours}h ${remMinutes}m"
    hours > 0 -> "${hours}h"
    else -> "${minutes}m"
  }
}