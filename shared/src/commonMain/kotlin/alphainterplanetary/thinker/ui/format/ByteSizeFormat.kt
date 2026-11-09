package alphainterplanetary.thinker.ui.format

import alphainterplanetary.thinker.util.formatDurationSeconds
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

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

/** A transfer rate for download progress, e.g. `3.2 MB/s`. */
fun formatTransferRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

/**
 * Time remaining for a transfer, or `null` when the inputs cannot produce a
 * meaningful estimate. Reuses [formatDurationSeconds] so the granularity matches
 * other durations.
 */
fun formatEta(bytesDone: Long, bytesTotal: Long?, throughputBps: Long?): String? {
  val total = bytesTotal ?: return null
  val rate = throughputBps ?: return null
  if (total <= 0L || rate <= 0L || bytesDone >= total) return null
  val remainingSeconds = ((total - bytesDone) / rate).coerceAtLeast(1L)
  return formatDurationSeconds(remainingSeconds.seconds)
}

private fun oneDecimal(value: Double): String {
  val tenths = (value * 10).roundToLong()
  return "${tenths / 10}.${tenths % 10}"
}
