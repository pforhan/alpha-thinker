package alphainterplanetary.thinker.engine

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

/**
 * Speed bands derived from average suite time on the eval harness.
 * Measured on device class in the evals; relative values matter across devices.
 */
enum class ModelSpeed {
  Fast,
  Fine,
  Slow,
  Unknown,
}

/**
 * Presentation grouping only — no model is left out of the browser, though
 * Discouraged starts collapsed and is revealed on request.
 * Recommended is the "good" tier (strong × fast).
 * High-quality but slower for strong models that aren't fast.
 * Lower-quality but fast for adequate models that are fast/fine.
 * Unknown for unmeasured/unclear models.
 * Discouraged for everything else.
 */
enum class ModelPresentationTier(val icon: String, val description: String) {
  Recommended("★", "Recommended"),
  HighQualityButSlower("✔", "High-quality but Slower"),
  LowerQualityButFast("▶▶", "Lower-quality but Fast"),
  Unknown("?", "Unknown"),
  Discouraged("✗", "Discouraged"),
  ;

  val presentationOrder: Int
    get() = ordinal
}

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

/**
 * Curated model profiles with measured seeds and family heuristics
 * for unmeasured models.
 */
object ModelProfiles {
  /** Measured seeds from docs/model-evals/all.md (pooled across runs). */
  /** Ordered by the eval report's recommendation grid. */
  val measuredSeeds = listOf(
    ModelMeasurement(
      modelIdPattern = "llama3.2:3b",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Fast,
      suiteSeconds = 5.33,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "phi4-mini:3.8b",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Fast,
      suiteSeconds = 7.43,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "qwen2.5:7b",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Fine,
      suiteSeconds = 15.4,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "phi3.5:3.8b",
      qualityScore = 96.7,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Fast,
      suiteSeconds = 8.65,
      okRate = 1.0,
      cleanRate = 0.889,
      jsonOkRate = 1.0,
      notes = listOf("Summaries include bullets/headings (simple-product)"),
    ),
    ModelMeasurement(
      modelIdPattern = "nemotron-mini:4b",
      qualityScore = 90.0,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Fast,
      suiteSeconds = 5.78,
      okRate = 1.0,
      cleanRate = 0.667,
      jsonOkRate = 1.0,
      notes = listOf("Titles wrapped in quotes (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4:e4b",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 82.79,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3.6:35b-mlx",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 208.53,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3.8:27b-mlx",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 264.69,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "muse-glimmer:30b-mlx",
      qualityScore = 100.0,
      qualityBand = ModelQuality.Strong,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 372.97,
      okRate = 1.0,
      cleanRate = 1.0,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "deepseek-r1:14b",
      qualityScore = 96.7,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 269.71,
      okRate = 1.0,
      cleanRate = 0.889,
      jsonOkRate = 1.0,
      notes = listOf("Summaries include bullets/headings (home-project)"),
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4:26b",
      qualityScore = 94.4,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 162.36,
      okRate = 0.889,
      cleanRate = 0.889,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4:31b-mlx",
      qualityScore = 94.4,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 260.42,
      okRate = 0.889,
      cleanRate = 0.889,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4:31b",
      qualityScore = 94.4,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 770.3,
      okRate = 0.889,
      cleanRate = 0.889,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "deepseek-r1:8b",
      qualityScore = 94.1,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 196.79,
      okRate = 1.0,
      cleanRate = 0.803,
      jsonOkRate = 1.0,
      notes = listOf("Multi-line titles (home-project, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "deepseek-r1:1.5b",
      qualityScore = 93.3,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Fine,
      suiteSeconds = 27.91,
      okRate = 1.0,
      cleanRate = 0.778,
      jsonOkRate = 1.0,
      notes = listOf("Titles over 60 chars (software-plan)", "Summaries include bullets/headings (home-project)"),
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4:26b-mlx",
      qualityScore = 85.2,
      qualityBand = ModelQuality.Adequate,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 90.74,
      okRate = 0.704,
      cleanRate = 0.704,
      jsonOkRate = 1.0,
    ),
    ModelMeasurement(
      modelIdPattern = "phi3:mini",
      qualityScore = 80.7,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 42.94,
      okRate = 0.867,
      cleanRate = 0.444,
      jsonOkRate = 1.0,
      notes = listOf("Titles wrapped in quotes (home-project)", "Titles with trailing periods (software-plan)", "Summaries include bullets/headings (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4:12b",
      qualityScore = 61.1,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 592.47,
      okRate = 0.556,
      cleanRate = 0.556,
      jsonOkRate = 0.667,
      notes = listOf("Questions failed to parse as JSON arrays (home-project)"),
    ),
    ModelMeasurement(
      modelIdPattern = "gemma4-12b-128k:12b",
      qualityScore = 61.1,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 626.29,
      okRate = 0.556,
      cleanRate = 0.556,
      jsonOkRate = 0.667,
      notes = listOf("Questions failed to parse as JSON arrays (home-project)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwq:32b",
      qualityScore = 56.5,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 414.07,
      okRate = 0.921,
      cleanRate = 0.158,
      jsonOkRate = 0.667,
      notes = listOf("Multi-line titles (home-project, simple-product, software-plan)", "Questions failed to parse as JSON arrays (home-project)", "Summaries over 150 words (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3.6:36b",
      qualityScore = 50.0,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 241.16,
      okRate = 0.667,
      cleanRate = 0.667,
      jsonOkRate = 0.333,
      notes = listOf("Questions failed to parse as JSON arrays (home-project, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwen2.5:1.5b",
      qualityScore = 38.9,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Fast,
      suiteSeconds = 4.03,
      okRate = 0.778,
      cleanRate = 0.222,
      jsonOkRate = 0.333,
      notes = listOf("Titles wrapped in quotes (home-project, simple-product)", "Questions failed to parse as JSON arrays (home-project, simple-product)", "Summaries include bullets/headings (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "tev1:0.8b",
      qualityScore = 35.6,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 109.74,
      okRate = 0.778,
      cleanRate = 0.111,
      jsonOkRate = 0.333,
      notes = listOf("Titles with trailing periods (home-project)", "Multi-line titles (simple-product, software-plan)", "Questions failed to parse as JSON arrays (simple-product, software-plan)", "Summaries include bullets/headings (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3.5:4b",
      qualityScore = 33.3,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 307.75,
      okRate = 0.333,
      cleanRate = 0.333,
      jsonOkRate = 0.333,
      notes = listOf("Questions failed to parse as JSON arrays (home-project, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3-vl:4b",
      qualityScore = 31.9,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 192.53,
      okRate = 0.639,
      cleanRate = 0.639,
      jsonOkRate = 0.0,
      notes = listOf("Questions failed to parse as JSON arrays (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "smollm2:135m",
      qualityScore = 20.0,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 88.79,
      okRate = 0.667,
      cleanRate = 0.222,
      jsonOkRate = 0.0,
      notes = listOf("Titles wrapped in quotes (simple-product)", "Titles with trailing periods (software-plan)", "Titles over 60 chars (home-project)", "Questions failed to parse as JSON arrays (home-project, simple-product, software-plan)", "Summaries over 150 words (simple-product)"),
    ),
    ModelMeasurement(
      modelIdPattern = "tinyllama:1b",
      qualityScore = 16.7,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 332.84,
      okRate = 0.667,
      cleanRate = 0.111,
      jsonOkRate = 0.0,
      notes = listOf("Titles over 60 chars (home-project, software-plan)", "Questions failed to parse as JSON arrays (home-project, simple-product, software-plan)", "Summaries include bullets/headings (home-project, simple-product)", "Summaries over 150 words (software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3.5:9.7b",
      qualityScore = 11.1,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 548.89,
      okRate = 0.222,
      cleanRate = 0.222,
      jsonOkRate = 0.0,
      notes = listOf("Questions failed to parse as JSON arrays (home-project, simple-product, software-plan)"),
    ),
    ModelMeasurement(
      modelIdPattern = "qwen3.5:0.8b",
      qualityScore = 0.0,
      qualityBand = ModelQuality.Weak,
      speedBand = ModelSpeed.Slow,
      suiteSeconds = 129.33,
      okRate = 0.0,
      cleanRate = 0.0,
      jsonOkRate = 0.0,
      notes = listOf("Questions failed to parse as JSON arrays (home-project, simple-product, software-plan)"),
    ),
  )

  /**
   * Family heuristics for unmeasured models.
   * Based on general model characteristics for the planning tasks
   * (title, JSON questions, summarization).
   */
  data class FamilyHeuristic(
    val familyPattern: Regex,
    val baseQuality: ModelQuality,
    val baseSpeed: ModelSpeed,
    val reasoning: String,
  )

  val familyHeuristics = listOf(
    FamilyHeuristic(
      Regex("^llama3\\.2|^llama-3\\.2", RegexOption.IGNORE_CASE),
      ModelQuality.Strong,
      ModelSpeed.Fast,
      "Llama 3.2 family; good instruction-following for structured tasks",
    ),
    FamilyHeuristic(
      Regex("^qwen2\\.5", RegexOption.IGNORE_CASE),
      ModelQuality.Strong,
      ModelSpeed.Fine,
      "Qwen 2.5 family; strong JSON/structured output",
    ),
    FamilyHeuristic(
      Regex("^qwen3(?!\\.5|\\.6|\\.8)", RegexOption.IGNORE_CASE),
      ModelQuality.Strong,
      ModelSpeed.Fine,
      "Qwen 3 family; solid for structured tasks",
    ),
    FamilyHeuristic(
      Regex("^gemma2|^gemma3", RegexOption.IGNORE_CASE),
      ModelQuality.Adequate,
      ModelSpeed.Fine,
      "Gemma family; decent instruction-following",
    ),
    FamilyHeuristic(
      Regex("^mistral|^mixtral", RegexOption.IGNORE_CASE),
      ModelQuality.Adequate,
      ModelSpeed.Fine,
      "Mistral family; good general instruction-following",
    ),
    FamilyHeuristic(
      Regex("^phi", RegexOption.IGNORE_CASE),
      ModelQuality.Adequate,
      ModelSpeed.Fast,
      "Phi family; efficient but may need careful prompting",
    ),
    FamilyHeuristic(
      Regex("^llama-3\\.1|^llama3\\.1", RegexOption.IGNORE_CASE),
      ModelQuality.Strong,
      ModelSpeed.Fine,
      "Llama 3.1 family; strong general performance",
    ),
  )

  fun findMeasurement(modelId: String): ModelMeasurement? {
    return measuredSeeds.firstOrNull { measurement ->
      val pattern = Regex(
        "^${Regex.escape(measurement.modelIdPattern.replace(":", "\\:"))}",
        RegexOption.IGNORE_CASE
      )
      pattern.containsMatchIn(modelId) || modelId.startsWith(measurement.modelIdPattern)
    }
  }

  fun findHeuristic(modelId: String): FamilyHeuristic? {
    return familyHeuristics.firstOrNull { it.familyPattern.containsMatchIn(modelId) }
  }

  /**
   * Measured models whose quality band is [ModelQuality.Strong]. Derived from [measuredSeeds].
   */
  val strongModelIds: List<String>
    get() = measuredSeeds
      .filter { it.qualityBand == ModelQuality.Strong }
      .map { it.modelIdPattern }
}
