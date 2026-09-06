package alphainterplanetary.thinker.util

import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual fun now(): Instant = Clock.System.now()

actual fun randomUUID(): String {
  val bytes = IntArray(16) { Random.nextInt(0, 256) }
  bytes[6] = (bytes[6] and 0x0f) or 0x40
  bytes[8] = (bytes[8] and 0x3f) or 0x80
  val sb = StringBuilder(36)
  for (i in 0 until 16) {
    if (i == 4 || i == 6 || i == 8 || i == 10) sb.append('-')
    sb.append(hexNibble(bytes[i] ushr 4))
    sb.append(hexNibble(bytes[i] and 0x0f))
  }
  return sb.toString()
}

private fun hexNibble(n: Int): Char = when (n) {
  in 0..9 -> '0' + n
  in 10..15 -> 'a' + (n - 10)
  else -> '0'
}
