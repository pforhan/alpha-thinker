package alphainterplanetary.thinker.util

/**
 * Formats a token count for display, rounding to the nearest tenth of a
 * thousand above 1k: `999` → `999`, `2048` → `2k`, `8192` → `8.2k`.
 */
fun formatTokens(tokens: Int): String =
  if (tokens >= 1000) "${(tokens / 100) / 10.0}k".removeSuffix(".0k") else "$tokens"
