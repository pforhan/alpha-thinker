package alphainterplanetary.thinker.engine.model

/**
 * Quality bands derived from the composite score (MODEL-CATALOG-PLAN.md).
 * Strong means it meets the bar for Verified/Recommended; Weak means
 * it struggles with JSON or formatting; Unknown for unmeasured models.
 */
enum class ModelQuality {
  Strong,
  Adequate,
  Weak,
  Unknown,
}
