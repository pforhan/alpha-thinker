package alphainterplanetary.thinker.util

fun String.normalizeWhitespace(): String = replace(Regex("\\s+"), " ").trim()

/** The JSON object contained in [text], if any (ignoring fences and prose). */
fun String.jsonObject(): String? {
  val start = indexOf('{')
  if (start == -1) return null

  var braceCount = 0
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
      '"' -> inString = true
      '{' -> braceCount++
      '}' -> {
        braceCount--
        if (braceCount == 0) {
          return substring(start, i + 1)
        }
      }
    }
  }

  return null
}
