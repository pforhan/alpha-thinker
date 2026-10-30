package alphainterplanetary.thinker.engine

/**
 * Ranks models using measured eval data when available, falling back to
 * family heuristics for unmeasured models (marked as estimated).
 * Presentation tiers follow the MODEL-CATALOG-PLAN: Verified/Recommended/Others.
 * Within a tier, better quality comes first and speed breaks ties.
 */
object ModelRanker {
  fun rank(models: List<RemoteModelInfo>): List<ModelRank> {
    return models.map { model -> rankModel(model) }
      .sortedWith(
        compareBy<ModelRank> { it.presentationTier.presentationOrder }
          .thenBy { qualityOrder(it.quality) }
          .thenBy { speedOrder(it.speed) }
          .thenBy { it.model.displayName },
      )
  }

  private fun rankModel(model: RemoteModelInfo): ModelRank {
    val measurement = ModelProfiles.findMeasurement(model.id)
      ?: ModelProfiles.findMeasurement(model.displayName)

    if (measurement != null) {
      val (presentationTier, reason) = deriveMeasuredTier(measurement)
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
      val (presentationTier, _) = deriveHeuristicTier(heuristic)
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
      presentationTier = ModelPresentationTier.Unknown,
      quality = ModelQuality.Unknown,
      speed = ModelSpeed.Unknown,
      reason = "Unmeasured model; performance unknown",
      estimated = true,
    )
  }

  private fun deriveMeasuredTier(
    measurement: ModelMeasurement,
  ): Pair<ModelPresentationTier, String> {
    val q = measurement.qualityBand
    val s = measurement.speedBand

    // Recommended = good tier from all.md: strong × fast
    if (q == ModelQuality.Strong && s == ModelSpeed.Fast) {
      return ModelPresentationTier.Recommended to buildReason("Reliable/fast", measurement)
    }
    // High-quality but slower: strong quality but fine or slow speed
    if (q == ModelQuality.Strong && (s == ModelSpeed.Fine || s == ModelSpeed.Slow)) {
      return ModelPresentationTier.HighQualityButSlower to buildReason("Reliable", measurement)
    }
    // Lower-quality but fast: adequate quality and fast or fine speeds
    if (q == ModelQuality.Adequate && (s == ModelSpeed.Fast || s == ModelSpeed.Fine)) {
      return ModelPresentationTier.LowerQualityButFast to buildReason(
        "Adequate quality, good speed",
        measurement
      )
    }
    // adequate × slow
    if (q == ModelQuality.Adequate && s == ModelSpeed.Slow) {
      return ModelPresentationTier.Discouraged to buildReason(
        "Adequate quality but slow",
        measurement
      )
    }
    // weak
    if (q == ModelQuality.Weak) {
      return ModelPresentationTier.Discouraged to buildReason("May struggle", measurement)
    }
    if (q == ModelQuality.Unknown || s == ModelSpeed.Unknown) {
      return ModelPresentationTier.Unknown to buildReason("Insufficient data", measurement)
    }

    return ModelPresentationTier.Discouraged to buildReason(
      "Meets minimum requirements",
      measurement
    )
  }

  private fun deriveHeuristicTier(
    heuristic: ModelProfiles.FamilyHeuristic,
  ): Pair<ModelPresentationTier, String> {
    val q = heuristic.baseQuality
    val s = heuristic.baseSpeed

    if (q == ModelQuality.Strong && s == ModelSpeed.Fast) {
      return ModelPresentationTier.Recommended to "${heuristic.reasoning} (estimated)"
    }
    if (q == ModelQuality.Strong && (s == ModelSpeed.Fine || s == ModelSpeed.Slow)) {
      return ModelPresentationTier.HighQualityButSlower to "${heuristic.reasoning} (estimated)"
    }
    if (q == ModelQuality.Adequate && (s == ModelSpeed.Fast || s == ModelSpeed.Fine)) {
      return ModelPresentationTier.LowerQualityButFast to "${heuristic.reasoning} (estimated)"
    }
    if (q == ModelQuality.Unknown || s == ModelSpeed.Unknown) {
      return ModelPresentationTier.Unknown to "${heuristic.reasoning} (estimated)"
    }

    return ModelPresentationTier.Discouraged to "${heuristic.reasoning} (estimated)"
  }

  private fun buildReason(base: String, measurement: ModelMeasurement): String {
    val notes = measurement.notes.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: ""
    return if (notes.isEmpty()) base else "$base ($notes)"
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
