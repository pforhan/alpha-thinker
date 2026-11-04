package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.engine.ModelPresentationTier
import alphainterplanetary.thinker.engine.ModelQuality
import alphainterplanetary.thinker.engine.ModelSpeed
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvalAggregationTest {
  private val fixture = EvalFixtures.fixtures.first()

  private val phone = EvalHardware(
    platform = "android",
    os = "Android",
    osVersion = "15",
    device = "Pixel 9",
    cpu = "Tensor G4",
    ramGb = 12.0,
    key = "android|android|15|pixel_9",
  )

  private val laptop = EvalHardware(
    platform = "desktop",
    os = "macOS",
    osVersion = "15.0",
    device = "MacBook Air",
    cpu = "M3",
    ramGb = 16.0,
    key = "desktop|macos|15.0|macbook_air",
  )

  private fun cell(
    interaction: EvalFixtures.Interaction,
    run: Int = 1,
    latencyS: Double = 1.0,
    raw: String? = null,
    error: String? = null,
    timeout: Boolean = false,
  ): EvalCell = EvalCell(
    fixture = fixture.name,
    interaction = interaction.wire,
    run = run,
    latencyS = latencyS,
    wallS = latencyS,
    raw = raw,
    error = error,
    timeout = timeout,
  )

  private fun row(
    cells: List<EvalCell>,
    hardware: EvalHardware = phone,
    modelId: String = "warped-community/Llama-3.2-3B-litert-lm",
    displayName: String = "Llama 3.2 3B",
    sizeBytes: Long? = 2_207_940_608L,
    parameterCount: Long? = 3_210_000_000L,
    quantization: String? = "INT4",
    runId: String = "run-1",
    startedAt: String = "2026-01-01T00:00:00Z",
    inProgress: Boolean = false,
  ): EvalRow = EvalRow(
    modelId = modelId,
    model = EvalModelMeta(
      sizeBytes = sizeBytes,
      parameterCount = parameterCount,
      quantization = quantization,
      displayName = displayName,
      sizeLabel = "3.2B",
    ),
    run = EvalRunMeta(id = runId, startedAt = startedAt, hardware = hardware),
    warmup = EvalWarmup(ok = true, loadS = 0.4),
    cells = cells,
    startedAt = startedAt,
    finishedAt = startedAt,
    inProgress = inProgress,
    deviceKey = hardware.keyOrDerived(),
  )

  private val cleanTitle = "Water Log"
  private val quotedTitle = "\"Water Log\""
  private val cleanQuestions = """["Who is it for?","What is the MVP?"]"""
  private val cleanSummary = "The user settled the scope, the goals, and the deadline."

  @Test
  fun `all-clean cells score a perfect quality and a strong band`() {
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(
          listOf(
            cell(EvalFixtures.Interaction.Title, raw = cleanTitle),
            cell(EvalFixtures.Interaction.InitialQuestions, raw = cleanQuestions),
            cell(EvalFixtures.Interaction.Summarize, raw = cleanSummary),
          ),
        ),
      ),
    )

    assertEquals(100.0, rollup.quality)
    assertEquals(ModelQuality.Strong, rollup.qualityBand)
    assertEquals(ModelSpeed.Fast, rollup.speedBand)
    assertEquals(ModelPresentationTier.Recommended, rollup.tier)
    assertEquals(1.0, rollup.okRate)
    assertEquals(1.0, rollup.cleanRate)
    assertEquals(1.0, rollup.jsonOkRate)
    assertEquals(1.0, rollup.arrayStringsRate)
    assertEquals(3, rollup.cells)
    assertEquals(0, rollup.errors)
    assertEquals(0, rollup.timeouts)
  }

  @Test
  fun `quality pools the cells rather than averaging rounded groups`() {
    // Three titles: one clean, one quoted (ok but not clean), one empty (not
    // even usable). ok = 2/3, clean = 1/3, and no question cells, so the
    // composite renormalises over the two rates that exist.
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(
          listOf(
            cell(EvalFixtures.Interaction.Title, raw = cleanTitle),
            cell(EvalFixtures.Interaction.Title, raw = quotedTitle),
            cell(EvalFixtures.Interaction.Title, raw = "  "),
          ),
        ),
      ),
    )

    assertEquals(46.7, rollup.quality)
    assertEquals(ModelQuality.Weak, rollup.qualityBand)
    assertNull(rollup.jsonOkRate)
    assertEquals(2.0 / 3.0, rollup.okRate!!, 0.0001)
    assertEquals(1.0 / 3.0, rollup.cleanRate!!, 0.0001)
  }

  @Test
  fun `suite seconds average the runs and skip timeouts`() {
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(
          listOf(
            cell(EvalFixtures.Interaction.Title, run = 1, latencyS = 1.0, raw = cleanTitle),
            cell(EvalFixtures.Interaction.Title, run = 2, latencyS = 2.0, raw = cleanTitle),
            cell(
              EvalFixtures.Interaction.InitialQuestions,
              run = 1,
              latencyS = 99.0,
              error = "timeout after 120s",
              timeout = true,
            ),
          ),
        ),
      ),
    )

    // Two runs of one measured title: (1 + 2) / 2. The timeout is neither a
    // usable latency nor part of a run's suite time.
    assertEquals(1.5, rollup.suiteS)
    assertEquals(1, rollup.timeouts)
    assertEquals(1, rollup.errors)
  }

  @Test
  fun `rows for the same model on different devices never pool`() {
    val rollups = EvalReport.analyze(
      listOf(
        row(listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)), hardware = phone),
        row(
          listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)),
          hardware = laptop,
          runId = "run-2",
        ),
      ),
    )

    assertEquals(2, rollups.size)
    assertEquals(setOf(phone.key, laptop.key), rollups.map { it.deviceKey }.toSet())
    val byDevice = EvalReport.byDevice(
      listOf(
        row(listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)), hardware = phone),
        row(
          listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)),
          hardware = laptop,
          runId = "run-2",
        ),
      ),
    )
    assertEquals(1, byDevice.getValue(phone.keyOrDerived()).size)
    assertEquals(1, byDevice.getValue(laptop.keyOrDerived()).size)
  }

  @Test
  fun `two rows for one model and device pool into one rollup`() {
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(
          listOf(cell(EvalFixtures.Interaction.Title, latencyS = 2.0, raw = cleanTitle)),
          runId = "run-1",
          startedAt = "2026-01-01T00:00:00Z",
        ),
        row(
          listOf(cell(EvalFixtures.Interaction.Title, latencyS = 4.0, raw = quotedTitle)),
          runId = "run-2",
          startedAt = "2026-01-02T00:00:00Z",
          inProgress = true,
        ),
      ),
    )

    assertEquals(2, rollup.rows)
    assertEquals(listOf("run-1", "run-2"), rollup.runIds.sorted())
    assertEquals(2, rollup.cells)
    assertEquals(3.0, rollup.suiteS)
    assertTrue(rollup.inProgress)
    assertEquals("2026-01-01T00:00:00Z", rollup.startedAt)
  }

  @Test
  fun `cold load pools across rows as a mean`() {
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)), runId = "run-1")
          .copy(warmup = EvalWarmup(ok = true, loadS = 0.2)),
        row(listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)), runId = "run-2")
          .copy(warmup = EvalWarmup(ok = true, loadS = 0.6)),
      ),
    )

    assertEquals(0.4, rollup.loadS!!, 0.0001)
  }

  @Test
  fun `speed and quality bands follow the documented thresholds`() {
    assertEquals(ModelSpeed.Fast, EvalReport.speedBand(9.99))
    assertEquals(ModelSpeed.Fine, EvalReport.speedBand(10.0))
    assertEquals(ModelSpeed.Fine, EvalReport.speedBand(34.99))
    assertEquals(ModelSpeed.Slow, EvalReport.speedBand(35.0))
    assertEquals(ModelSpeed.Unknown, EvalReport.speedBand(null))

    assertEquals(ModelQuality.Strong, EvalReport.qualityBand(98.0))
    assertEquals(ModelQuality.Adequate, EvalReport.qualityBand(97.99))
    assertEquals(ModelQuality.Adequate, EvalReport.qualityBand(85.0))
    assertEquals(ModelQuality.Weak, EvalReport.qualityBand(84.99))
    assertEquals(ModelQuality.Unknown, EvalReport.qualityBand(null))
  }

  @Test
  fun `the tier is a label on the quality and speed grid`() {
    assertEquals(
      ModelPresentationTier.Recommended,
      EvalReport.presentationTier(ModelQuality.Strong, ModelSpeed.Fast),
    )
    assertEquals(
      ModelPresentationTier.HighQualityButSlower,
      EvalReport.presentationTier(ModelQuality.Strong, ModelSpeed.Fine),
    )
    assertEquals(
      ModelPresentationTier.HighQualityButSlower,
      EvalReport.presentationTier(ModelQuality.Strong, ModelSpeed.Slow),
    )
    assertEquals(
      ModelPresentationTier.LowerQualityButFast,
      EvalReport.presentationTier(ModelQuality.Adequate, ModelSpeed.Fast),
    )
    assertEquals(
      ModelPresentationTier.LowerQualityButFast,
      EvalReport.presentationTier(ModelQuality.Adequate, ModelSpeed.Fine),
    )
    assertEquals(
      ModelPresentationTier.Discouraged,
      EvalReport.presentationTier(ModelQuality.Adequate, ModelSpeed.Slow),
    )
    assertEquals(
      ModelPresentationTier.Discouraged,
      EvalReport.presentationTier(ModelQuality.Weak, ModelSpeed.Fast),
    )
    assertEquals(
      ModelPresentationTier.Unknown,
      EvalReport.presentationTier(ModelQuality.Unknown, ModelSpeed.Fast),
    )
  }

  @Test
  fun `notes report the formatting habits behind a lower clean rate`() {
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(
          listOf(
            cell(EvalFixtures.Interaction.Title, raw = quotedTitle),
            cell(EvalFixtures.Interaction.Summarize, raw = "Here is the summary:\n- scope"),
            cell(EvalFixtures.Interaction.Summarize, raw = "Here is the summary: the scope is settled."),
          ),
        ),
      ),
    )

    assertContains(rollup.notes, "Titles wrapped in quotes (simple-product)")
    assertContains(rollup.notes, "Summaries include bullets/headings (simple-product)")
    assertContains(rollup.notes, "Summaries include preamble (simple-product)")
  }

  @Test
  fun `the markdown report groups by device and carries the row's numbers`() {
    val markdown = EvalReport.markdown(
      EvalReport.analyze(
        listOf(
          row(listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)), hardware = phone),
          row(
            listOf(cell(EvalFixtures.Interaction.Title, raw = cleanTitle)),
            hardware = laptop,
            runId = "run-2",
          ),
        ),
      ),
    )

    assertContains(markdown, "# On-device model evals")
    assertContains(markdown, "## Android 15 · Pixel 9 · Tensor G4 · 12.0GB")
    assertContains(markdown, "## macOS 15.0 · MacBook Air · M3 · 16.0GB")
    assertContains(markdown, "| Llama 3.2 3B | 2.1 GB | 100.0 | Recommended |")
  }

  @Test
  fun `the curated snippet emits the measurement with its bands`() {
    val rollup = EvalReport.analyzeGroup(
      listOf(
        row(
          listOf(
            cell(EvalFixtures.Interaction.Title, raw = cleanTitle),
            cell(EvalFixtures.Interaction.InitialQuestions, raw = cleanQuestions),
          ),
        ),
      ),
    )

    val snippet = EvalReport.curatedSnippet(rollup)

    assertContains(snippet, "ModelMeasurement(")
    assertContains(snippet, "modelIdPattern = \"warped-community/Llama-3.2-3B-litert-lm\"")
    assertContains(snippet, "qualityScore = 100.0")
    assertContains(snippet, "qualityBand = ModelQuality.Strong")
    assertContains(snippet, "speedBand = ModelSpeed.Fast")
  }
}
