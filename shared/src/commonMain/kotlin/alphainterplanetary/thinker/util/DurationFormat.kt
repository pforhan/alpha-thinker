package alphainterplanetary.thinker.util

import kotlin.time.Duration

/**
 * Formats a duration for the phase timeline to the largest of minutes, hours, or days,
 * truncating leftovers, with a minimum of 1 minute.
 *
 * Some examples:
 *  * 30s would render as "1m"
 *  * 23h 59m would render as "23h"
 *  * 1d 3h would render as "1d"
 */
fun formatDuration(duration: Duration): String {
  val minutes = duration.inWholeMinutes.coerceAtLeast(0)
  if (minutes < 1) return "1m"
  val hours = minutes / 60
  val days = hours / 24
  return when {
    days > 0 -> "${days}d"
    hours > 0 -> "${hours}h"
    else -> "${minutes}m"
  }
}

/**
 * Formats a task duration with sub-minute precision for the Task Manager
 * ("42s", "3m 05s", "1h 20m") so short generation work reads as live.
 */
fun formatTaskDuration(duration: Duration): String {
  val seconds = duration.inWholeSeconds.coerceAtLeast(0)
  if (seconds < 60) return "${seconds}s"
  val minutes = seconds / 60
  if (minutes < 60) {
    val remainderSeconds = seconds % 60
    return if (remainderSeconds > 0) {
      "${minutes}m ${remainderSeconds}s"
    } else {
      "${minutes}m"
    }
  }
  val remainingMinutes = minutes % 60
  return if (remainingMinutes > 0) {
    "${minutes / 60}h ${remainingMinutes}m"
  } else {
    "${minutes / 60}h"
  }
}