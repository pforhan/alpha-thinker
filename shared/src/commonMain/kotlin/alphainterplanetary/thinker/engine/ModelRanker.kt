package alphainterplanetary.thinker.engine

/**
 * Ranks models using measured eval data when available, falling back to
 * family heuristics for unmeasured models (marked as estimated).
 * Presentation tiers follow the MODEL-CATALOG-PLAN: Verified/Recommended/Others.
 * No model is ever hidden.
 */
object ModelRanker {
  fun rank(models: List<RemoteModelInfo>): List<ModelRank> {
    return models.map { model -> rankModel(model) }
      .sortedWith(
        compareBy<ModelRank> { presentationOrder(it.presentationTier) }
          .thenByDescending { qualityOrder(it.quality) }
          .thenBy { speedOrder(it.speed) }
          .thenBy { it.model.displayName },
      )
  }

  private fun rankModel(model: RemoteModelInfo): ModelRank {
    val measurement = ModelProfiles.findMeasurement(model.id)
      ?: ModelProfiles.findMeasurement(model.displayName)

    if (measurement != null) {
      val (presentationTier, reason) = deriveMeasuredTier(measurement, model)
      return ModelRank(
        model = model,
        presentationTier = presentationTier,
        quality = measurement.qualityBand,
        speed = measurement.speedBand,
        reason = reason,
        estimated = false,
      )
    }

    val heuristic = ModelProfiles.findHeuristic(model.id)
      ?: ModelProfiles.findHeuristic(model.displayName)

    if (heuristic != null) {
      val (presentationTier, reason) = deriveHeuristicTier(heuristic, model)
      return ModelRank(
        model = model,
        presentationTier = presentationTier,
        quality = heuristic.baseQuality,
        speed = heuristic.baseSpeed,
        reason = "${heuristic.reasoning} (estimated)",
        estimated = true,
      )
    }

    // Unrecognized, unmeasured models
    return ModelRank(
      model = model,
      presentationTier = ModelPresentationTier.Others,
      quality = ModelQuality.Unknown,
      speed = ModelSpeed.Unknown,
      reason = "Unmeasured model; performance unknown",
      estimated = true,
    )
  }

  private fun deriveMeasuredTier(
    measurement: ModelMeasurement,
    model: RemoteModelInfo,
  ): Pair<ModelPresentationTier, String> {
    val q = measurement.qualityBand
    val s = measurement.speedBand

    // good = strong × fast
    if (q == ModelQuality.Strong && s == ModelSpeed.Fast) {
      return ModelPresentationTier.Verified to buildReason("Strong and fast; excellent for title/JSON/summarization", measurement)
    }
    // ok = strong × fine or adequate × fast
    if ((q == ModelQuality.Strong && s == ModelSpeed.Fine) ||
        (q == ModelQuality.Adequate && s == ModelSpeed.Fast)
    ) {
      return ModelPresentationTier.Recommended to buildReason("Good balance of quality and speed", measurement)
    }
    // strong × slow but still solid quality
    if (q == ModelQuality.Strong && s == ModelSpeed.Slow) {
      // Some strong/slow are large models; still recommended if quality is top-tier
      if (measurement.qualityScore >= 100.0) {
        return ModelPresentationTier.Recommended to buildReason("Top quality but slower; best if speed isn't critical", measurement)
      }
      return ModelPresentationTier.Others to buildReason("Strong quality but slow", measurement)
    }
    // adequate × fine
    if (q == ModelQuality.Adequate && s == ModelSpeed.Fine) {
      return ModelPresentationTier.Recommended to buildReason("Adequate quality, reasonable speed", measurement)
    }
    // adequate × slow
    if (q == ModelQuality.Adequate && s == ModelSpeed.Slow) {
      return ModelPresentationTier.Others to buildReason("Adequate quality but slow", measurement)
    }
    // weak
    if (q == ModelQuality.Weak) {
      return ModelPresentationTier.Others to buildReason("May struggle with JSON/formatting", measurement)
    }

    return ModelPresentationTier.Others to buildReason("Meets minimum requirements", measurement)
  }

  private fun deriveHeuristicTier(
    heuristic: ModelProfiles.FamilyHeuristic,
    model: RemoteModelInfo,
  ): Pair<ModelPresentationTier, String> {
    val q = heuristic.baseQuality
    val s = heuristic.baseSpeed

    if (q == ModelQuality.Strong && s == ModelSpeed.Fast) {
      return ModelPresentationTier.Verified to "${heuristic.reasoning} (estimated)"
    }
    if ((q == ModelQuality.Strong && s == ModelSpeed.Fine) ||
        (q == ModelQuality.Adequate && s == ModelSpeed.Fast)
    ) {
      return ModelPresentationTier.Recommended to "${heuristic.reasoning} (estimated)"
    }
    if (q == ModelQuality.Strong && s == ModelSpeed.Slow) {
      return ModelPresentationTier.Recommended to "${heuristic.reasoning} (estimated)"
    }

    return ModelPresentationTier.Others to "${heuristic.reasoning} (estimated)"
  }

  private fun buildReason(base: String, measurement: ModelMeasurement): String {
    val notes = measurement.notes.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: ""
    return if (notes.isEmpty()) base else "$base ($notes)"
  }

  private fun presentationOrder(tier: ModelPresentationTier): Int = when (tier) {
    ModelPresentationTier.Verified -> 0
    ModelPresentationTier.Recommended -> 1
    ModelPresentationTier.Others -> 2
  }

  private fun qualityOrder(quality: ModelQuality): Int = when (quality) {
    ModelQuality.Strong -> 0
    ModelQuality.Adequate -> 1
    ModelQuality.Weak -> 2
    ModelQuality.Unknown -> 3
  }

  private fun speedOrder(speed: ModelSpeed): Int = when (speed) {
    ModelSpeed.Fast -> 0
    ModelSpeed.Fine -> 1
    ModelSpeed.Slow -> 2
    ModelSpeed.Unknown -> 3
  }
}
