package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.engine.ModelPresentationTier
import alphainterplanetary.thinker.engine.ModelQuality
import alphainterplanetary.thinker.engine.ModelSpeed
import kotlin.math.roundToLong

/** One cell paired with the score derived from its raw reply. */
data class EvalScoredCell(
  val cell: EvalCell,
  val eval: EvalScore,
)

/** Rates and latency distribution over one `(fixture, interaction)` bucket. */
data class EvalGroupStats(
  val fixture: String,
  val interaction: String,
  val reps: Int,
  val okRate: Double?,
  val cleanRate: Double?,
  val jsonOkRate: Double?,
  val arrayStringsRate: Double?,
  val timeouts: Int,
  val errors: Int,
  val sleeps: Int,
  val aborted: Int,
  val latencyMeanS: Double,
  val latencyP50S: Double,
  val latencyP95S: Double,
  val latencyStdS: Double,
  val latencyMinS: Double,
  val latencyMaxS: Double,
  val genTpsMedian: Double?,
)

/**
 * A model's result on one device: the quality and speed axes, the band each
 * falls in, and the presentation tier that is only a label on their grid.
 */
data class EvalRollup(
  val modelId: String,
  val displayName: String,
  val deviceKey: String,
  val hardware: EvalHardware,
  val runIds: List<String>,
  val rows: Int,
  val startedAt: String?,
  val inProgress: Boolean,
  val sizeBytes: Long?,
  val sizeLabel: String,
  val parameterSize: String?,
  val quantization: String?,
  val estimatedGb: Double?,
  val cells: Int,
  val timeouts: Int,
  val errors: Int,
  val sleeps: Int,
  val aborted: Int,
  val okRate: Double?,
  val cleanRate: Double?,
  val jsonOkRate: Double?,
  val arrayStringsRate: Double?,
  val latencyMeanS: Double,
  val latencyP95S: Double,
  val suiteS: Double?,
  val genTpsMedian: Double?,
  val loadS: Double?,
  val quality: Double?,
  val qualityBand: ModelQuality,
  val speedBand: ModelSpeed,
  val tier: ModelPresentationTier,
  val notes: List<String>,
  val groups: List<EvalGroupStats>,
)

/**
 * Aggregation of stored rows, derived from the raw replies at read time.
 *
 * It is a port of `analyze_row`/`analyze_models` in
 * `tools/eval_ollama_models.py`: model-level rates pool their cells directly
 * rather than averaging rounded group rates, so the composite score cannot
 * drift with how the cells happened to be bucketed. Rows are pooled per
 * `(model, device)` — never across devices — because a speed band measured on a
 * desktop says nothing about the same model on a phone.
 */
object EvalReport {
  private data class QualityWeight(val weight: Double, val field: (EvalScore) -> Boolean?)

  private val qualityWeights = listOf(
    QualityWeight(0.5) { it.jsonOk },
    QualityWeight(0.3) { it.clean },
    QualityWeight(0.2) { it.ok },
  )

  fun analyze(rows: List<EvalRow>): List<EvalRollup> =
    rows.groupBy { it.modelId to it.deviceKeyOrDerived() }
      .map { (_, group) -> analyzeGroup(group) }
      .sortedWith(
        compareBy<EvalRollup> { it.tier.presentationOrder }
          .thenByDescending { it.quality ?: -1.0 }
          .thenBy { it.suiteS ?: Double.MAX_VALUE }
          .thenBy { it.displayName },
      )

  fun byDevice(rows: List<EvalRow>): Map<String, List<EvalRollup>> =
    analyze(rows).groupBy { it.deviceKey }

  fun analyzeGroup(rows: List<EvalRow>): EvalRollup {
    require(rows.isNotEmpty()) { "At least one row is required" }
    val base = rows.first()
    val cells = rows.flatMap { it.cells }
    val scored = cells.map { cell ->
      val interaction = EvalFixtures.Interaction.fromWire(cell.interaction)
      EvalScoredCell(
        cell = cell,
        // A cell the harness cut short is a failure, not a scorable partial
        // reply: a loop that happens to start with valid JSON did not answer
        // the question. Everything else is re-scored from its raw text.
        eval = if (cell.aborted != null) {
          EvalScore(error = cell.error ?: cell.aborted)
        } else {
          interaction?.let { EvalScoring.score(it, cell.raw.orEmpty()) }
            ?: EvalScore(error = "unknown interaction")
        },
      )
    }
    val questions = scored.filter { it.cell.interaction == EvalFixtures.Interaction.InitialQuestions.wire }
    val byKey = scored.groupBy { it.cell.fixture to it.cell.interaction }
    val groups = byKey.map { (key, items) -> groupStats(key.first, key.second, items) }
      .sortedWith(compareBy({ it.fixture }, { interactionOrder(it.interaction) }))

    val model = base.model
    val warmup = pooledWarmup(rows)
    val estimatedGb = EvalMemory.estimateGb(
      sizeBytes = model.sizeBytes,
      parameterCount = model.parameterCount,
      quantization = model.quantization,
    )
    val okRate = pooledRate(scored) { it.ok }
    val cleanRate = pooledRate(scored) { it.clean }
    val jsonOkRate = questions.takeIf { it.isNotEmpty() }?.let { pooledRate(it) { score -> score.jsonOk } }
    val arrayStringsRate = questions.takeIf { it.isNotEmpty() }
      ?.let { pooledRate(it) { score -> score.arrayStrings } }
    val quality = compositeQuality(okRate, cleanRate, jsonOkRate)
    val suiteS = suiteSeconds(rows)
    val qualityBand = qualityBand(quality)
    val speedBand = speedBand(suiteS)
    val groupTps = groups.mapNotNull { it.genTpsMedian }

    return EvalRollup(
      modelId = base.modelId,
      displayName = model.displayName ?: base.modelId,
      deviceKey = base.deviceKeyOrDerived(),
      hardware = base.run.hardware,
      runIds = rows.map { it.run.id }.distinct(),
      rows = rows.size,
      startedAt = rows.mapNotNull { it.startedAt }.minOrNull(),
      inProgress = rows.any { it.inProgress },
      sizeBytes = model.sizeBytes,
      sizeLabel = sizeLabel(model.sizeBytes, model.sizeLabel ?: model.parameterSize, estimatedGb),
      parameterSize = model.parameterSize,
      quantization = model.quantization,
      estimatedGb = estimatedGb,
      cells = cells.size,
      timeouts = cells.count { isTimeout(it) } + if (warmup?.timeout == true) 1 else 0,
      errors = cells.count { it.error != null } + if (warmup?.error != null) 1 else 0,
      sleeps = cells.count { it.sleepS != null && it.sleepS > 0.0 } +
        if (warmup?.sleepS != null && warmup.sleepS > 0.0) 1 else 0,
      aborted = cells.count { it.aborted != null },
      okRate = okRate,
      cleanRate = cleanRate,
      jsonOkRate = jsonOkRate,
      arrayStringsRate = arrayStringsRate,
      latencyMeanS = mean(groups.map { it.latencyMeanS }),
      latencyP95S = groups.maxOfOrNull { it.latencyP95S } ?: 0.0,
      suiteS = suiteS,
      genTpsMedian = groupTps.takeIf { it.isNotEmpty() }?.let(::median),
      loadS = warmup?.loadS,
      quality = quality,
      qualityBand = qualityBand,
      speedBand = speedBand,
      tier = presentationTier(qualityBand, speedBand),
      notes = formatNotes(scored),
      groups = groups,
    )
  }

  fun qualityBand(quality: Double?): ModelQuality = when {
    quality == null -> ModelQuality.Unknown
    quality >= 98.0 -> ModelQuality.Strong
    quality >= 85.0 -> ModelQuality.Adequate
    else -> ModelQuality.Weak
  }

  fun speedBand(suiteS: Double?): ModelSpeed = when {
    suiteS == null -> ModelSpeed.Unknown
    suiteS < 10.0 -> ModelSpeed.Fast
    suiteS < 35.0 -> ModelSpeed.Fine
    else -> ModelSpeed.Slow
  }

  fun presentationTier(quality: ModelQuality, speed: ModelSpeed): ModelPresentationTier = when {
    quality == ModelQuality.Strong && speed == ModelSpeed.Fast ->
      ModelPresentationTier.Recommended

    quality == ModelQuality.Strong && (speed == ModelSpeed.Fine || speed == ModelSpeed.Slow) ->
      ModelPresentationTier.HighQualityButSlower

    quality == ModelQuality.Adequate && (speed == ModelSpeed.Fast || speed == ModelSpeed.Fine) ->
      ModelPresentationTier.LowerQualityButFast

    quality == ModelQuality.Unknown || speed == ModelSpeed.Unknown ->
      ModelPresentationTier.Unknown

    else -> ModelPresentationTier.Discouraged
  }

  /** A one-line device description for headings, not a grouping key. */
  fun deviceLabel(hardware: EvalHardware): String = listOfNotNull(
    hardware.os?.takeIf { it.isNotBlank() }?.let { os ->
      hardware.osVersion?.takeIf { it.isNotBlank() }?.let { "$os $it" } ?: os
    },
    hardware.device?.takeIf { it.isNotBlank() },
    hardware.cpu?.takeIf { it.isNotBlank() },
    hardware.ramGb?.let { "${oneDecimal(it)}GB" },
  ).joinToString(" · ").ifBlank { "Unknown device" }

  fun markdown(rollups: List<EvalRollup>): String {
    if (rollups.isEmpty()) return "# Model evals\n\nNo rows yet.\n"
    val byDevice = rollups.groupBy { it.deviceKey }
    return buildString {
      appendLine("# On-device model evals")
      appendLine()
      appendLine("Grouped by hardware and OS so a per-device recommendation is not")
      appendLine("diluted by a faster or slower machine.")
      byDevice.forEach { (key, deviceRollups) ->
        appendLine()
        appendLine("## ${deviceLabel(deviceRollups.first().hardware)}")
        appendLine()
        appendLine("Device key: `$key`")
        appendLine()
        appendLine("| model | size | quality | tier | suite_s | ok | clean | json | notes |")
        appendLine("|---|---|---|---|---|---|---|---|---|")
        deviceRollups.forEach { rollup ->
          appendLine(
            "| ${escape(rollup.displayName)} | ${rollup.sizeLabel} | " +
              "${format(rollup.quality)} | ${rollup.tier.description} | ${format(rollup.suiteS)} | " +
              "${format(rollup.okRate)} | ${format(rollup.cleanRate)} | " +
              "${format(rollup.jsonOkRate)} | ${escape(rollup.notes.joinToString("; "))} |",
          )
        }
      }
    }
  }

  fun curatedSnippet(rollup: EvalRollup): String {
    val quality = rollup.quality ?: return ""
    val notes = rollup.notes.joinToString(", ") { "\"${it.replace("\"", "\\\"")}\"" }
    return buildString {
      appendLine("    ModelMeasurement(")
      appendLine("      modelIdPattern = \"${rollup.modelId}\",")
      appendLine("      qualityScore = ${format(quality)},")
      appendLine("      qualityBand = ModelQuality.${rollup.qualityBand.name},")
      appendLine("      speedBand = ModelSpeed.${rollup.speedBand.name},")
      appendLine("      suiteSeconds = ${rollup.suiteS?.let(::format) ?: "null"},")
      appendLine("      okRate = ${rollup.okRate?.let(::format) ?: "null"},")
      appendLine("      cleanRate = ${rollup.cleanRate?.let(::format) ?: "null"},")
      appendLine("      jsonOkRate = ${rollup.jsonOkRate?.let(::format) ?: "null"},")
      if (notes.isNotEmpty()) appendLine("      notes = listOf($notes),")
      appendLine("    ),")
    }.trimEnd()
  }

  private fun groupStats(
    fixture: String,
    interaction: String,
    items: List<EvalScoredCell>,
  ): EvalGroupStats {
    // Timing distribution over successful requests only; an error or timeout
    // latency is capped by the timeout, not a real generation time.
    val okItems = items.filter { it.cell.error == null }.ifEmpty { items }
    val latencies = okItems.map { it.cell.latencyS ?: 0.0 }
    return EvalGroupStats(
      fixture = fixture,
      interaction = interaction,
      reps = items.size,
      okRate = rate(items) { it.ok },
      cleanRate = rate(items) { it.clean },
      jsonOkRate = rate(items) { it.jsonOk },
      arrayStringsRate = rate(items) { it.arrayStrings },
      timeouts = items.count { isTimeout(it.cell) },
      errors = items.count { it.cell.error != null },
      sleeps = items.count { it.cell.sleepS != null && it.cell.sleepS > 0.0 },
      aborted = items.count { it.cell.aborted != null },
      latencyMeanS = mean(latencies),
      latencyP50S = round(median(latencies), 4),
      latencyP95S = round(p95(latencies), 4),
      latencyStdS = round(pstdev(latencies), 4),
      latencyMinS = round(latencies.minOrNull() ?: 0.0, 4),
      latencyMaxS = round(latencies.maxOrNull() ?: 0.0, 4),
      genTpsMedian = okItems.mapNotNull { it.cell.timings?.genTps }.takeIf { it.isNotEmpty() }
        ?.let { round(median(it), 2) },
    )
  }

  private fun rate(
    items: List<EvalScoredCell>,
    selector: (EvalScore) -> Boolean?,
  ): Double? {
    val values = items.map { selector(it.eval) }
    if (values.all { it == null }) return null
    return round(values.count { it == true }.toDouble() / values.size, 3)
  }

  private fun pooledRate(
    items: List<EvalScoredCell>,
    selector: (EvalScore) -> Boolean? = { it.ok },
  ): Double? {
    if (items.isEmpty()) return null
    val values = items.map { selector(it.eval) }
    if (values.all { it == null }) return null
    return values.count { it == true }.toDouble() / values.size
  }

  private fun compositeQuality(
    okRate: Double?,
    cleanRate: Double?,
    jsonOkRate: Double?,
  ): Double? {
    var total = 0.0
    var weightSum = 0.0
    val values = listOf(jsonOkRate, cleanRate, okRate)
    qualityWeights.forEachIndexed { index, weight ->
      val value = values[index] ?: return@forEachIndexed
      total += weight.weight * value
      weightSum += weight.weight
    }
    if (weightSum == 0.0) return null
    return round(100.0 * total / weightSum, 1)
  }

  private fun suiteSeconds(rows: List<EvalRow>): Double? {
    // Each row is one sweep and its cell `run` numbers are local to it, so the
    // pass key has to include the row's run id. Without the re-key, run 1 of
    // batch A and run 1 of batch B would be summed as the same pass.
    val byRun = mutableMapOf<String, Double>()
    rows.forEach { row ->
      row.cells
        .filter { !isTimeout(it) && it.error == null && it.latencyS != null }
        .forEach { cell ->
          val key = "${row.run.id}#${cell.run}"
          byRun[key] = (byRun[key] ?: 0.0) + (cell.latencyS ?: 0.0)
        }
    }
    if (byRun.isEmpty()) return null
    return round(byRun.values.average(), 3)
  }

  /**
   * Collapse several cold-load measurements into one, as `merge_rows` does.
   * Cold load is the noisiest timing, so it pools as a mean and stays `ok` only
   * when every contributing load succeeded.
   */
  private fun pooledWarmup(rows: List<EvalRow>): EvalWarmup? {
    val warmups = rows.mapNotNull { it.warmup }
    if (warmups.isEmpty()) return null
    val loads = warmups.mapNotNull { it.loadS }
    val walls = warmups.mapNotNull { it.wallS }
    val sleeps = warmups.mapNotNull { it.sleepS }
    return EvalWarmup(
      ok = warmups.all { it.ok },
      wallS = walls.takeIf { it.isNotEmpty() }?.average(),
      sleepS = sleeps.takeIf { it.isNotEmpty() }?.sum(),
      error = warmups.firstNotNullOfOrNull { it.error },
      timeout = warmups.any { it.timeout },
      loadS = loads.takeIf { it.isNotEmpty() }?.average(),
    )
  }

  /** One note's evidence: how many cells showed the problem and where. */
  private class Counter(var count: Int = 0, val fixtures: MutableSet<String> = mutableSetOf())

  private fun formatNotes(scored: List<EvalScoredCell>): List<String> {
    val titleQuotes = Counter()
    val titlePeriod = Counter()
    val titlePreamble = Counter()
    val titleMultiline = Counter()
    val titleLong = Counter()
    val questionsJson = Counter()
    val questionsArray = Counter()
    val summaryBullets = Counter()
    val summaryPreamble = Counter()
    val summaryLong = Counter()
    var titleTotal = 0
    var questionsTotal = 0
    var summaryTotal = 0

    scored.forEach { item ->
      val interaction = item.cell.interaction
      val fixture = item.cell.fixture
      val error = item.eval.error.orEmpty().lowercase()
      when (interaction) {
        EvalFixtures.Interaction.Title.wire -> {
          titleTotal++
          when {
            "wrapped in quotes" in error -> titleQuotes.record(fixture)
            "trailing period" in error -> titlePeriod.record(fixture)
            "preamble" in error -> titlePreamble.record(fixture)
            "multi-line" in error -> titleMultiline.record(fixture)
            ">60 chars" in error -> titleLong.record(fixture)
          }
        }

        EvalFixtures.Interaction.InitialQuestions.wire -> {
          questionsTotal++
          when {
            "json parse failed" in error -> questionsJson.record(fixture)
            "array of non-empty strings" in error -> questionsArray.record(fixture)
          }
        }

        EvalFixtures.Interaction.Summarize.wire -> {
          summaryTotal++
          when {
            "bullets/headings" in error -> summaryBullets.record(fixture)
            "preamble" in error -> summaryPreamble.record(fixture)
            ">150 words" in error -> summaryLong.record(fixture)
          }
        }
      }
    }

    return buildList {
      addNote(this, titleQuotes, titleTotal, "Titles wrapped in quotes")
      addNote(this, titlePeriod, titleTotal, "Titles with trailing periods")
      addNote(this, titlePreamble, titleTotal, "Titles include preamble/explanation")
      addNote(this, titleMultiline, titleTotal, "Multi-line titles")
      addNote(this, titleLong, titleTotal, "Titles over 60 chars")
      addNote(this, questionsJson, questionsTotal, "Questions failed to parse as JSON arrays")
      addNote(
        this,
        questionsArray,
        questionsTotal,
        "Questions not valid arrays of non-empty strings",
      )
      addNote(this, summaryBullets, summaryTotal, "Summaries include bullets/headings")
      addNote(this, summaryPreamble, summaryTotal, "Summaries include preamble")
      addNote(this, summaryLong, summaryTotal, "Summaries over 150 words")
      val aborted = scored.count { it.cell.aborted != null }
      if (aborted > 0) add("$aborted cell(s) stopped after a repetition loop")
    }
  }

  private fun Counter.record(fixture: String) {
    count++
    fixtures += fixture
  }

  private fun addNote(
    target: MutableList<String>,
    counter: Counter,
    total: Int,
    label: String,
  ) {
    if (counter.count == 0 || total == 0) return
    if (counter.count.toDouble() / total < 0.25 && counter.fixtures.size < 2) return
    val fixtures = counter.fixtures.sorted().joinToString(", ")
    target += if (fixtures.isEmpty()) label else "$label ($fixtures)"
  }

  private fun isTimeout(cell: EvalCell): Boolean =
    cell.timeout || cell.error?.contains("timeout", ignoreCase = true) == true

  private fun interactionOrder(interaction: String): Int =
    EvalFixtures.Interaction.entries.indexOfFirst { it.wire == interaction }
      .takeIf { it >= 0 } ?: Int.MAX_VALUE

  private fun sizeLabel(sizeBytes: Long?, fallback: String?, estimatedGb: Double?): String =
    when {
      sizeBytes != null && sizeBytes >= 1_073_741_824L ->
        "${oneDecimal(sizeBytes / 1_073_741_824.0)} GB"

      sizeBytes != null && sizeBytes >= 1_048_576L ->
        "${(sizeBytes / 1_048_576.0).roundToLong()} MB"

      sizeBytes != null -> "${(sizeBytes / 1024.0).roundToLong()} KB"
      estimatedGb != null -> "${oneDecimal(estimatedGb)} GB*"
      !fallback.isNullOrBlank() -> fallback
      else -> "?"
    }

  private fun mean(values: List<Double>): Double =
    if (values.isEmpty()) 0.0 else round(values.average(), 4)

  private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle]
    else (sorted[middle - 1] + sorted[middle]) / 2.0
  }

  private fun p95(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    return sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
  }

  private fun pstdev(values: List<Double>): Double {
    if (values.size < 2) return 0.0
    val average = values.average()
    val variance = values.sumOf { (it - average) * (it - average) } / values.size
    return kotlin.math.sqrt(variance)
  }

  private fun round(value: Double, places: Int): Double {
    var factor = 1.0
    repeat(places) { factor *= 10 }
    return (value * factor).roundToLong() / factor
  }

  private fun oneDecimal(value: Double): String = round(value, 1).toString()

  private fun format(value: Double?): String = when (value) {
    null -> "-"
    else -> round(value, 2).toString()
  }

  private fun escape(text: String): String = text.replace("|", "\\|")
}
