package alphainterplanetary.thinker.engine.model

/**
 * Measured data for a model family/id from eval runs.
 * Seeds are loaded from the eval harness's report output.
 */
data class ModelMeasurement(
  val modelIdPattern: String,
  val qualityScore: Double,
  val qualityBand: ModelQuality,
  val speedBand: ModelSpeed,
  val suiteSeconds: Double?,
  val okRate: Double,
  val cleanRate: Double,
  val jsonOkRate: Double?,
  val notes: List<String> = emptyList(),
)
