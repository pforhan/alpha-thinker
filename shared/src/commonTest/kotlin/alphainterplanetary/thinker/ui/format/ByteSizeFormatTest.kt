package alphainterplanetary.thinker.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

  @Test
  fun `formatTransferRate appends per second`() {
    assertEquals("1.5 MB/s", formatTransferRate(1_500_000L))
  }

  @Test
  fun `formatEta returns null without enough information`() {
    assertNull(formatEta(50L, null, 10L))
    assertNull(formatEta(50L, 100L, null))
    assertNull(formatEta(50L, 100L, 0L))
    assertNull(formatEta(100L, 100L, 10L))
  }

  @Test
  fun `formatEta formats remaining time from smoothed throughput`() {
    assertEquals("5s", formatEta(50L, 100L, 10L))
    assertEquals("1m", formatEta(0L, 90L, 1L))
  }
}
