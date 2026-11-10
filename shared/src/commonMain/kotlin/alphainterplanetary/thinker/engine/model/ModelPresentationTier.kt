package alphainterplanetary.thinker.engine.model

/**
 * Presentation grouping only — no model is left out of the browser, though
 * Discouraged starts collapsed and is revealed on request.
 * Recommended is the "good" tier (strong × fast).
 * High-quality but slower for strong models that aren't fast.
 * Lower-quality but fast for adequate models that are fast/fine.
 * Unknown for unmeasured/unclear models.
 * Discouraged for everything else.
 */
enum class ModelPresentationTier(val description: String) {
  Recommended("Recommended"),
  HighQualityButSlower("High-quality but Slower"),
  LowerQualityButFast("Lower-quality but Fast"),
  Unknown("Unknown"),
  Discouraged("Discouraged"),
  ;

  val presentationOrder: Int
    get() = ordinal
}
