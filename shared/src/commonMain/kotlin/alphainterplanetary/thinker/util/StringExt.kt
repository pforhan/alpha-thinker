package alphainterplanetary.thinker.util

fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ").trim()

/** The first JSON array contained in this string, if any (ignoring fences and prose). */
fun String.jsonArray(): String? {
  val start = indexOf('[')
  if (start == -1) return null

  var depth = 0
  var inString = false
  var isEscaped = false

  for (i in start until length) {
    val char = this[i]

    if (inString) {
      when {
        isEscaped -> {
          isEscaped = false
        }
        char == '\\' -> {
          isEscaped = true
        }
        char == '"' -> {
          inString = false
        }
      }
      continue
    }

    when (char) {
      // Brackets inside a JSON string are content, not structure, so a question
      // like "What about [draft]?" doesn't truncate the array at its first `]`.
      '"' -> inString = true
      '[' -> depth++
      ']' -> {
        depth--
        if (depth == 0) {
          return substring(start, i + 1)
        }
      }
    }
  }

  return null
}
