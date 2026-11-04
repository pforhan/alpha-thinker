package alphainterplanetary.thinker.eval

import ai.koog.prompt.dsl.prompt
import alphainterplanetary.thinker.di.DeviceInfo
import alphainterplanetary.thinker.di.PlatformContext
import alphainterplanetary.thinker.engine.EngineInstance
import alphainterplanetary.thinker.engine.KoogPlanningBackend
import alphainterplanetary.thinker.engine.LocalBackendFactory
import alphainterplanetary.thinker.engine.LocalModel
import alphainterplanetary.thinker.engine.LocalModelStore
import alphainterplanetary.thinker.util.now
import alphainterplanetary.thinker.util.randomUUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What a sweep is doing right now, or what the last one left behind. */
sealed interface ModelEvalState {
  data object Idle : ModelEvalState

  data class Running(
    val modelLabel: String,
    val modelNumber: Int,
    val modelCount: Int,
    val cellsDone: Int,
    val cellsTotal: Int,
    val detail: String,
  ) : ModelEvalState

  data class Finished(
    val models: Int,
    val cells: Int,
  ) : ModelEvalState

  data class Failed(
    val message: String,
    val outOfMemory: Boolean = false,
  ) : ModelEvalState

  data object Cancelled : ModelEvalState
}

/** The LAN share's own state, so the report can stay readable while it runs. */
sealed interface EvalShareState {
  data object Idle : EvalShareState
  data object Sending : EvalShareState
  data class Sent(val message: String) : EvalShareState
  data class Failed(val message: String) : EvalShareState
}

/** The installed-model questions the evaluator asks, kept as a seam for tests. */
interface EvalModelSource {
  val catalog: List<LocalModel>
  fun isInstalled(model: LocalModel): Boolean
  fun instance(model: LocalModel): EngineInstance?
}

/** The production source: the curated catalog plus the repository's instances. */
class LocalStoreEvalModelSource(
  private val store: LocalModelStore,
) : EvalModelSource {
  override val catalog: List<LocalModel>
    get() = store.catalog

  override fun isInstalled(model: LocalModel): Boolean = store.isInstalled(model)

  override fun instance(model: LocalModel): EngineInstance? = store.instance(model)
}

/**
 * Runs the Python harness's suite against locally installed LiteRT models.
 *
 * Everything measurable is measured here and written down immediately: one JSON
 * row per `(model, run)` under the platform's eval directory, flushed after the
 * warmup and after every cell. A sweep killed by the process being reclaimed —
 * the expected outcome for a model too large for the phone — therefore leaves a
 * row with its warmup, the cells that landed, and `in_progress: true`, which
 * the next launch can resume instead of repeating.
 *
 * The suite itself, the prompts, and the scoring live in [EvalFixtures],
 * [EvalScoring], and [EvalReport] as pure common code. This class is only the
 * orchestration: which installed model, which fresh backend, which cell, and
 * how long to wait for it.
 */
class ModelEvaluator(
  private val models: EvalModelSource,
  private val backendFactory: LocalBackendFactory,
  private val platform: PlatformContext,
  private val scope: CoroutineScope,
  private val store: EvalRowStore = EvalRowStore(platform),
) {
  private val _state = MutableStateFlow<ModelEvalState>(ModelEvalState.Idle)
  val state: StateFlow<ModelEvalState> = _state.asStateFlow()

  private val _share = MutableStateFlow<EvalShareState>(EvalShareState.Idle)
  val share: StateFlow<EvalShareState> = _share.asStateFlow()

  private val _rows = MutableStateFlow<List<EvalRow>>(emptyList())

  /** Every stored row, newest first, across models and runs. */
  val rows: StateFlow<List<EvalRow>> = _rows.asStateFlow()

  private var job: Job? = null

  init {
    refresh()
  }

  /** The curated models whose file is present on this device. */
  fun installedModels(): List<LocalModel> =
    models.catalog.filter { models.isInstalled(it) }

  /** Whether [model] still has an interrupted row that can be picked up. */
  fun hasInterrupted(model: LocalModel): Boolean = _rows.value.any { row ->
    row.modelId == model.id && row.inProgress
  }

  fun refresh() {
    _rows.value = store.loadAll().sortedByDescending { it.startedAt.orEmpty() }
  }

  /**
   * Runs every installed [models] entry that is selected, resuming interrupted
   * rows unless [force] asks for a fresh sweep.
   *
   * A running sweep is left alone rather than replaced: an evaluation is minutes
   * of real inference, and silently cancelling it because a button was tapped
   * twice is worse than ignoring the second tap.
   */
  fun start(
    models: List<LocalModel>,
    runs: Int = 1,
    force: Boolean = false,
    cellTimeout: Duration = 120.seconds,
    loadTimeout: Duration = 600.seconds,
  ) {
    if (_state.value is ModelEvalState.Running) return
    val installed = models.filter { this.models.isInstalled(it) }
    if (installed.isEmpty()) {
      _state.value = ModelEvalState.Failed("No installed local models to evaluate.")
      return
    }
    val runId = newRunId()
    val startedAt = now().toString()
    val effectiveRuns = runs.coerceAtLeast(1)
    _share.value = EvalShareState.Idle
    _state.value = ModelEvalState.Running(
      modelLabel = installed.first().label,
      modelNumber = 1,
      modelCount = installed.size,
      cellsDone = 0,
      cellsTotal = EvalFixtures.groups.size * effectiveRuns * installed.size,
      detail = "Starting",
    )
    val previous = job
    job = scope.launch {
      // A cancelled sweep flushes its partial row while it unwinds; wait for
      // that to land so this sweep's resume lookup cannot race it.
      previous?.join()
      runSweep(installed, effectiveRuns, force, runId, startedAt, cellTimeout, loadTimeout)
    }
  }

  /**
   * Stops after the cell in flight. The partial row is saved as in-progress, so
   * the same model can be resumed rather than restarted.
   */
  fun cancel() {
    if (_state.value !is ModelEvalState.Running) return
    _state.value = ModelEvalState.Cancelled
    job?.cancel()
  }

  /** POSTs every stored row to a LAN receiver (see `tools/eval_receiver.py`). */
  fun share(baseUrl: String) {
    val rows = _rows.value
    if (rows.isEmpty()) {
      _share.value = EvalShareState.Failed("No evaluation rows to share yet.")
      return
    }
    _share.value = EvalShareState.Sending
    scope.launch {
      val result = EvalRemote.post(baseUrl, rows)
      _share.value = if (result.ok) {
        EvalShareState.Sent(result.message)
      } else {
        EvalShareState.Failed(result.message)
      }
    }
  }

  fun clearShareState() {
    _share.value = EvalShareState.Idle
  }

  private suspend fun runSweep(
    models: List<LocalModel>,
    runs: Int,
    force: Boolean,
    runId: String,
    startedAt: String,
    cellTimeout: Duration,
    loadTimeout: Duration,
  ) {
    var cellsMeasured = 0
    // The bar tracks the whole sweep, not the model: with several models the
    // completed count carries over while the total stays fixed.
    val total = EvalFixtures.groups.size * runs * models.size
    models.forEachIndexed { index, model ->
      currentCoroutineContext().ensureActive()
      _state.value = ModelEvalState.Running(
        modelLabel = model.label,
        modelNumber = index + 1,
        modelCount = models.size,
        cellsDone = cellsMeasured,
        cellsTotal = total,
        detail = "Loading ${model.label}",
      )
      val measured = try {
        runModel(
          model = model,
          runs = runs,
          force = force,
          runId = runId,
          startedAt = startedAt,
          cellTimeout = cellTimeout,
          loadTimeout = loadTimeout,
          onCell = { done, detail ->
            _state.value = ModelEvalState.Running(
              modelLabel = model.label,
              modelNumber = index + 1,
              modelCount = models.size,
              cellsDone = cellsMeasured + done,
              cellsTotal = total,
              detail = detail,
            )
          },
        )
      } catch (e: CancellationException) {
        throw e
      } catch (t: Throwable) {
        _state.value = ModelEvalState.Failed(
          message = t.describeFailure(),
          outOfMemory = t.isOutOfMemory(),
        )
        return
      }
      cellsMeasured += measured
    }
    _state.value = ModelEvalState.Finished(models = models.size, cells = cellsMeasured)
    refresh()
  }

  /** Runs one model's suite and returns how many cells it now has. */
  private suspend fun runModel(
    model: LocalModel,
    runs: Int,
    force: Boolean,
    runId: String,
    startedAt: String,
    cellTimeout: Duration,
    loadTimeout: Duration,
    onCell: (done: Int, detail: String) -> Unit,
  ): Int {
    val instance = models.instance(model)
      ?: throw IllegalStateException("${model.label} has no installed engine instance.")
    val hardware = platform.deviceInfo().toEvalHardware()
    var row = if (force) {
      newRow(model, instance, hardware, runId, startedAt, runs)
    } else {
      findResumable(model, hardware, runs) ?: newRow(model, instance, hardware, runId, startedAt, runs)
    }
    val cells = row.cells.toMutableList()
    val backend = backendFactory.create(instance)
    try {
      if (row.warmup?.ok != true) {
        val warmup = try {
          measureWarmup(backend, loadTimeout)
        } catch (e: CancellationException) {
          throw e
        } catch (t: Throwable) {
          // An Error during the cold load is exactly the "too big for this
          // phone" case: leave the row behind before the process dies.
          row = row.copy(warmup = EvalWarmup(ok = false, error = t.describeFailure()))
          publishQuietly(row)
          throw t
        }
        row = row.copy(warmup = warmup)
        publish(row)
      }
      EvalFixtures.groups.forEach { (interaction, fixture) ->
        for (run in 1..runs) {
          currentCoroutineContext().ensureActive()
          val alreadyMeasured = !force && cells.any {
            it.fixture == fixture.name && it.interaction == interaction.wire && it.run == run
          }
          if (alreadyMeasured) continue
          onCell(
            cells.size,
            "${interaction.wire}/${fixture.name} run $run/$runs",
          )
          val cell = try {
            measureCell(
              backend = backend,
              interaction = interaction,
              fixture = fixture,
              run = run,
              timeout = cellTimeout,
            )
          } catch (e: CancellationException) {
            throw e
          } catch (t: Throwable) {
            // An Error (an OOM, typically) is not a model reply: record what
            // happened as a cell, flush the row, and let the sweep stop.
            cells += EvalCell(
              fixture = fixture.name,
              interaction = interaction.wire,
              run = run,
              error = t.describeFailure(),
            )
            row = row.copy(cells = cells.toList())
            publishQuietly(row)
            throw t
          }
          cells += cell
          row = row.copy(cells = cells.toList())
          publish(row)
        }
      }
      row = row.copy(inProgress = false, finishedAt = now().toString())
      publish(row)
      return cells.size
    } catch (e: CancellationException) {
      // Leave the row resumable: cancelling is "stop here", not "throw away
      // the cells that already landed".
      publishQuietly(row.copy(cells = cells.toList(), inProgress = true))
      throw e
    } finally {
      runCatching { backend.close() }
    }
  }

  private suspend fun measureWarmup(
    backend: KoogPlanningBackend,
    timeout: Duration,
  ): EvalWarmup {
    val mark = TimeSource.Monotonic.markNow()
    return try {
      val reply = withTimeoutOrNull(timeout) {
        execute(backend, EvalFixtures.WarmupPrompt)
      }
      val wall = mark.elapsedSeconds()
      if (reply == null) {
        EvalWarmup(
          ok = false,
          wallS = wall,
          error = "timeout after ${timeout.inWholeSeconds}s",
          timeout = true,
        )
      } else {
        EvalWarmup(ok = true, wallS = wall, timings = reply.timings(wall))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      EvalWarmup(ok = false, wallS = mark.elapsedSeconds(), error = e.describeFailure())
    }
  }

  private suspend fun measureCell(
    backend: KoogPlanningBackend,
    interaction: EvalFixtures.Interaction,
    fixture: EvalFixtures.Fixture,
    run: Int,
    timeout: Duration,
  ): EvalCell {
    val text = EvalFixtures.prompt(interaction, fixture)
    val mark = TimeSource.Monotonic.markNow()
    return try {
      val reply = withTimeoutOrNull(timeout) { execute(backend, text) }
      val wall = mark.elapsedSeconds()
      if (reply == null) {
        EvalCell(
          fixture = fixture.name,
          interaction = interaction.wire,
          run = run,
          latencyS = wall,
          wallS = wall,
          error = "timeout after ${timeout.inWholeSeconds}s",
          timeout = true,
        )
      } else {
        EvalCell(
          fixture = fixture.name,
          interaction = interaction.wire,
          run = run,
          latencyS = wall,
          wallS = wall,
          timings = reply.timings(wall),
          raw = reply.text,
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val wall = mark.elapsedSeconds()
      EvalCell(
        fixture = fixture.name,
        interaction = interaction.wire,
        run = run,
        latencyS = wall,
        wallS = wall,
        error = e.describeFailure(),
      )
    }
  }

  private suspend fun execute(backend: KoogPlanningBackend, text: String): EvalReply {
    val message = backend.executor.execute(
      prompt("model-eval") { user(text) },
      backend.model,
    )
    return EvalReply(
      text = message.textContent().trim(),
      inputTokens = message.metaInfo.inputTokensCount,
      outputTokens = message.metaInfo.outputTokensCount,
    )
  }

  private fun findResumable(
    model: LocalModel,
    hardware: EvalHardware,
    runs: Int,
  ): EvalRow? = _rows.value
    .filter { row ->
      row.modelId == model.id &&
        row.inProgress &&
        row.schema == EvalFixtures.Schema &&
        row.fixturesVersion == EvalFixtures.Version &&
        row.run.runs == runs &&
        row.deviceKeyOrDerived() == hardware.keyOrDerived()
    }
    .maxByOrNull { it.startedAt.orEmpty() }

  private fun newRow(
    model: LocalModel,
    instance: EngineInstance,
    hardware: EvalHardware,
    runId: String,
    startedAt: String,
    runs: Int,
  ): EvalRow = EvalRow(
    modelId = model.id,
    model = EvalModelMeta(
      parameterSize = parameterSizeLabel(model),
      sizeLabel = parameterSizeLabel(model),
      parameterCount = model.parameterCount,
      basename = model.label,
      quantization = model.quantization,
      family = model.id.substringAfter('/'),
      format = "litertlm",
      contextLength = model.contextTokens,
      sizeBytes = model.expectedBytes,
      displayName = model.label,
      backend = "litert-lm",
    ),
    run = EvalRunMeta(
      id = runId,
      startedAt = startedAt,
      runs = runs,
      numCtx = instance.contextTokens.takeIf { it > 0 },
      temperature = 0.0,
      loadTimeout = null,
      script = "app",
      hardware = hardware,
    ),
    startedAt = startedAt,
    inProgress = true,
    deviceKey = hardware.keyOrDerived(),
  )

  private fun publish(row: EvalRow) {
    store.save(row)
    _rows.update { current ->
      (current.filterNot { it.modelId == row.modelId && it.run.id == row.run.id } + row)
        .sortedByDescending { it.startedAt.orEmpty() }
    }
  }

  private fun publishQuietly(row: EvalRow) {
    runCatching { publish(row) }
  }

  private fun TimeMark.elapsedSeconds(): Double =
    elapsedNow().inWholeMicroseconds / 1_000_000.0

  private fun EvalReply.timings(wallS: Double): EvalTimings = EvalTimings(
    totalS = wallS,
    promptEvalCount = inputTokens,
    evalCount = outputTokens,
    genTps = outputTokens?.takeIf { it > 0 && wallS > 0.0 }?.let { tokens ->
      (tokens / wallS * 100.0).toLong() / 100.0
    },
  )

  private data class EvalReply(
    val text: String,
    val inputTokens: Int?,
    val outputTokens: Int?,
  )

  private fun newRunId(): String {
    val stamp = now().toString().replace(Regex("[^0-9]"), "").take(14)
    return "app-$stamp-${randomUUID().take(6)}"
  }

  private fun parameterSizeLabel(model: LocalModel): String? {
    val open = model.label.lastIndexOf('(')
    val close = model.label.lastIndexOf(')')
    return if (open >= 0 && close > open) {
      model.label.substring(open + 1, close).trim().ifBlank { null }
    } else {
      null
    }
  }
}

private fun DeviceInfo.toEvalHardware(): EvalHardware = EvalHardware(
  platform = platform,
  os = os,
  osVersion = osVersion,
  device = device,
  cpu = cpu,
  ramGb = ramGb,
  appVersion = appVersion,
  apiLevel = apiLevel,
  key = key(),
)

/**
 * OOM is an [Error] on the JVM and a runtime abort on native, so common code
 * cannot name the type. The class name and the message are what every platform
 * leaves behind, and recognizing either is enough to report "out of memory"
 * rather than a generic crash.
 */
private fun Throwable.isOutOfMemory(): Boolean {
  val name = this::class.simpleName.orEmpty()
  return name.contains("OutOfMemory", ignoreCase = true) ||
    message?.contains("out of memory", ignoreCase = true) == true
}

private fun Throwable.describeFailure(): String {
  val message = message?.takeIf { it.isNotBlank() }
  return when {
    message != null -> "${this::class.simpleName}: $message"
    else -> this::class.simpleName ?: "unknown failure"
  }.take(500)
}
