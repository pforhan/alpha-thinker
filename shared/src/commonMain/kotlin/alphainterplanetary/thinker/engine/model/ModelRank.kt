package alphainterplanetary.thinker.engine.model

/**
 * A model ranked by curation rules, with derived axes and reasoning.
 */
data class ModelRank(
  val model: RemoteModelInfo,
  val presentationTier: ModelPresentationTier,
  val quality: ModelQuality,
  val speed: ModelSpeed,
  val reason: String,
  /** True if the ranking is estimated from heuristics rather than measured eval data. */
  val estimated: Boolean = false,
)
