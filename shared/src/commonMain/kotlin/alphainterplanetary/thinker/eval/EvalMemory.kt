package alphainterplanetary.thinker.eval

import kotlin.math.roundToLong

/**
 * The coarse footprint estimate behind the "will it fit?" flag.
 *
 * It is deliberately approximate: the exact resident size depends on the
 * runtime, the KV cache, and the graph, none of which a downloaded file tells
 * us. Prefer the on-disk weight size when the harness has it, otherwise derive
 * it from the parameter count and quantization, then add the same 15% plus one
 * gigabyte of headroom the Python harness uses so a phone row and a desktop row
 * read the same way.
 */
object EvalMemory {
  private const val DefaultQuantBits = 4.5

  private val quantBits = mapOf(
    "Q2_K" to 2.6,
    "Q3_K_S" to 3.5,
    "Q3_K_M" to 3.9,
    "Q3_K_L" to 4.3,
    "Q4_0" to 4.5,
    "Q4_1" to 5.0,
    "Q4_K_S" to 4.5,
    "Q4_K_M" to 4.5,
    "Q5_0" to 5.5,
    "Q5_K_S" to 5.5,
    "Q5_K_M" to 5.5,
    "Q6_K" to 6.6,
    "Q8_0" to 8.5,
    "INT4" to 4.5,
    "INT8" to 8.5,
    "NVFP4" to 4.0,
    "NVFP8" to 8.0,
    "F16" to 16.0,
    "BF16" to 16.0,
    "F32" to 32.0,
  )

  fun estimateGb(
    sizeBytes: Long?,
    parameterCount: Long?,
    quantization: String?,
  ): Double? {
    val weightsGb = when {
      sizeBytes != null && sizeBytes > 0L -> sizeBytes / GigaByte
      parameterCount != null && parameterCount > 0L -> {
        val bits = quantBits[quantization?.uppercase()] ?: DefaultQuantBits
        parameterCount * bits / 8.0 / GigaByte
      }

      else -> return null
    }
    return ((weightsGb * 1.15 + 1.0) * 10).roundToLong() / 10.0
  }

  private const val GigaByte = 1_073_741_824.0
}
