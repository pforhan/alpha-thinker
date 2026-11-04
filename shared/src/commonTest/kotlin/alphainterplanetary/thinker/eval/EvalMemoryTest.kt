package alphainterplanetary.thinker.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EvalMemoryTest {
  @Test
  fun `the on-disk size wins when the harness has it`() {
    // 2 GiB of weights, plus the harness's 15% runtime overhead and 1 GB of
    // headroom.
    assertEquals(
      3.3,
      EvalMemory.estimateGb(
        sizeBytes = 2L * 1_073_741_824L,
        parameterCount = 1L,
        quantization = "Q4_0",
      ),
    )
  }

  @Test
  fun `without a size the weights are derived from parameters and quantization`() {
    // 3B at INT4 is 3e9 * 4.5 / 8 bytes ~= 1.57 GiB, then 15% + 1 GB.
    assertEquals(
      2.8,
      EvalMemory.estimateGb(
        sizeBytes = null,
        parameterCount = 3_000_000_000L,
        quantization = "INT4",
      ),
    )
  }

  @Test
  fun `an unknown quantization assumes the harness default`() {
    val unknown = EvalMemory.estimateGb(
      sizeBytes = null,
      parameterCount = 3_000_000_000L,
      quantization = "SOMETHING_NEW",
    )
    val default = EvalMemory.estimateGb(
      sizeBytes = null,
      parameterCount = 3_000_000_000L,
      quantization = null,
    )

    assertEquals(default, unknown)
  }

  @Test
  fun `a bigger quantization estimates a bigger footprint`() {
    val int4 = EvalMemory.estimateGb(null, 3_000_000_000L, "INT4")!!
    val q8 = EvalMemory.estimateGb(null, 3_000_000_000L, "Q8_0")!!
    val f16 = EvalMemory.estimateGb(null, 3_000_000_000L, "F16")!!

    assertEquals(true, int4 < q8)
    assertEquals(true, q8 < f16)
  }

  @Test
  fun `nothing known means no estimate`() {
    assertNull(EvalMemory.estimateGb(sizeBytes = null, parameterCount = null, quantization = null))
    assertNull(EvalMemory.estimateGb(sizeBytes = 0L, parameterCount = 0L, quantization = "INT4"))
  }

  @Test
  fun `a zero size falls through to the parameter estimate`() {
    assertEquals(
      EvalMemory.estimateGb(null, 1_540_000_000L, "Q8_0"),
      EvalMemory.estimateGb(0L, 1_540_000_000L, "Q8_0"),
    )
  }
}
