package alphainterplanetary.thinker.ui.format

import kotlin.math.roundToLong

/**
 * A file size for the model rows: decimal units with one decimal place where
 * the number is large enough to need it, so `2.2 GB` reads the way the hosting
 * site labels the same file.
 */
fun formatBytes(bytes: Long): String = when {
  bytes >= 1_000_000_000L -> "${oneDecimal(bytes / 1_000_000_000.0)} GB"
  bytes >= 1_000_000L -> "${oneDecimal(bytes / 1_000_000.0)} MB"
  bytes >= 1_000L -> "${oneDecimal(bytes / 1_000.0)} KB"
  else -> "$bytes B"
}

private fun oneDecimal(value: Double): String {
  val tenths = (value * 10).roundToLong()
  return "${tenths / 10}.${tenths % 10}"
}
