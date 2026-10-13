package alphainterplanetary.thinker.util

/**
 * Formats a token count for display as a whole number with a `k` (thousand) or
 * `m` (million) suffix, so a context window stays short and never carries a
 * decimal: `999` → `999`, `2048` → `2k`, `131072` → `131k`, `1_000_000` → `1m`.
 *
 * Values below a thousand are shown as they are; larger ones floor to whole
 * thousands, and from a million to whole millions, matching the floored
 * precision [formatDuration] uses.
 */
fun formatTokens(tokens: Int): String = when {
  tokens >= 1_000_000 -> "${tokens / 1_000_000}m"
  tokens >= 1_000 -> "${tokens / 1_000}k"
  else -> "$tokens"
}
