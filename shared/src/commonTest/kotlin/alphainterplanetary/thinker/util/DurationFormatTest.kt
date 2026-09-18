package alphainterplanetary.thinker.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DurationFormatTest {

  @Test
  fun `formatDuration shows less than a minute as less-than-one`() {
    assertEquals("<1m", formatDuration(45.seconds))
  }

  @Test
  fun `formatDuration shows minutes only when under an hour`() {
    assertEquals("42m", formatDuration(42.minutes))
  }

  @Test
  fun `formatDuration shows hours and minutes when under a day`() {
    assertEquals("4h 20m", formatDuration((4 * 60 + 20).minutes))
  }

  @Test
  fun `formatDuration shows hours only when no remainder minutes`() {
    assertEquals("5h", formatDuration((5 * 60).minutes))
  }

  @Test
  fun `formatDuration shows days and hours when days present`() {
    assertEquals("1d 3h", formatDuration((24 + 3).minutes * 60))
  }

  @Test
  fun `formatDuration shows days only when no remainder hours`() {
    assertEquals("3d", formatDuration((3 * 24).hours))
  }

  @Test
  fun `formatDuration floors to whole minutes`() {
    assertEquals("<1m", formatDuration(59.seconds + 999.milliseconds))
  }
}