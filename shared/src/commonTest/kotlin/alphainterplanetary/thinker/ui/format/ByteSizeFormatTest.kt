package alphainterplanetary.thinker.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeFormatTest {

  @Test
  fun `small sizes stay in bytes`() {
    assertEquals("0 B", formatBytes(0L))
    assertEquals("999 B", formatBytes(999L))
  }

  @Test
  fun `sizes step through decimal units`() {
    assertEquals("1.0 KB", formatBytes(1_000L))
    assertEquals("1.2 KB", formatBytes(1_234L))
    assertEquals("1.6 MB", formatBytes(1_597_922L))
    assertEquals("2.2 GB", formatBytes(2_207_940_608L))
  }

  @Test
  fun `units round to one decimal place`() {
    assertEquals("1.5 KB", formatBytes(1_500L))
    assertEquals("3.9 GB", formatBytes(3_910_090_752L))
  }
}
