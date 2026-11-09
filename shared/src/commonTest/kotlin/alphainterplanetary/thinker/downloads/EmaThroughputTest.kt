package alphainterplanetary.thinker.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmaThroughputTest {

  @Test
  fun `first sample establishes a baseline without a rate`() {
    val ema = EmaThroughput()
    assertNull(ema.sample(bytesDone = 0L, nowNanos = 0L))
  }

  @Test
  fun `second sample returns instantaneous throughput`() {
    val ema = EmaThroughput()
    ema.sample(bytesDone = 0L, nowNanos = 0L)
    assertEquals(1_000L, ema.sample(bytesDone = 1_000L, nowNanos = 1_000_000_000L))
  }

  @Test
  fun `samples are smoothed with an exponential moving average`() {
    val ema = EmaThroughput(alpha = 0.5)
    ema.sample(bytesDone = 0L, nowNanos = 0L)
    ema.sample(bytesDone = 100L, nowNanos = 1_000_000_000L) // 100 B/s
    val smoothed = ema.sample(bytesDone = 400L, nowNanos = 2_000_000_000L) // instant 300 B/s
    assertEquals(200L, smoothed)
  }

  @Test
  fun `non advancing samples keep the current rate`() {
    val ema = EmaThroughput()
    ema.sample(bytesDone = 0L, nowNanos = 0L)
    val rate = ema.sample(bytesDone = 500L, nowNanos = 1_000_000_000L)
    assertEquals(rate, ema.sample(bytesDone = 500L, nowNanos = 2_000_000_000L))
    assertEquals(rate, ema.sample(bytesDone = 500L, nowNanos = 2_000_000_000L))
  }

  @Test
  fun `a later spike is damped rather than reported raw`() {
    val ema = EmaThroughput(alpha = 0.3)
    ema.sample(bytesDone = 0L, nowNanos = 0L)
    ema.sample(bytesDone = 1_000L, nowNanos = 1_000_000_000L) // 1000 B/s
    val smoothed = ema.sample(bytesDone = 11_000L, nowNanos = 2_000_000_000L) // instant 10000 B/s
    assertTrue(smoothed != null && smoothed > 1_000L && smoothed < 10_000L)
  }
}
