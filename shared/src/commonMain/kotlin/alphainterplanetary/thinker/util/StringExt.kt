package alphainterplanetary.thinker.util

fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ").trim()