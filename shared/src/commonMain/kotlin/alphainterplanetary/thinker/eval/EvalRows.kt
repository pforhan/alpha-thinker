package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.di.DeviceInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One measured interaction. The raw reply is stored verbatim and the score is
 * derived at read time, so a scoring rule can change without re-running a sweep.
 */
@Serializable
data class EvalCell(
  val fixture: String,
  val interaction: String,
  val run: Int,
  @SerialName("latency_s") val latencyS: Double? = null,
  @SerialName("wall_s") val wallS: Double? = null,
  @SerialName("sleep_s") val sleepS: Double? = null,
  val timings: EvalTimings? = null,
  val raw: String? = null,
  val error: String? = null,
  val timeout: Boolean = false,
)

/** The unscored request that loads the model and measures that cold start. */
@Serializable
data class EvalWarmup(
  val ok: Boolean = false,
  @SerialName("wall_s") val wallS: Double? = null,
  @SerialName("sleep_s") val sleepS: Double? = null,
  val timings: EvalTimings? = null,
  val error: String? = null,
  val timeout: Boolean = false,
  @SerialName("load_s") val loadS: Double? = null,
)

/**
 * Provider-independent timing counters. Ollama fills the load/prompt/eval
 * split; LiteRT only exposes wall time and token counts, so the fields a
 * backend cannot measure stay null rather than being fabricated.
 */
@Serializable
data class EvalTimings(
  @SerialName("total_s") val totalS: Double? = null,
  @SerialName("load_s") val loadS: Double? = null,
  @SerialName("prompt_eval_count") val promptEvalCount: Int? = null,
  @SerialName("prompt_eval_s") val promptEvalS: Double? = null,
  @SerialName("eval_count") val evalCount: Int? = null,
  @SerialName("eval_s") val evalS: Double? = null,
  @SerialName("gen_tps") val genTps: Double? = null,
)

/** The model metadata a row carries, mirroring the Python harness's shape. */
@Serializable
data class EvalModelMeta(
  @SerialName("parameter_size") val parameterSize: String? = null,
  @SerialName("size_label") val sizeLabel: String? = null,
  @SerialName("parameter_count") val parameterCount: Long? = null,
  val basename: String? = null,
  val finetune: String? = null,
  val quantization: String? = null,
  val family: String? = null,
  val format: String? = null,
  @SerialName("context_length") val contextLength: Int? = null,
  val capabilities: List<String>? = null,
  @SerialName("size_bytes") val sizeBytes: Long? = null,
  val digest: String? = null,
  @SerialName("display_name") val displayName: String? = null,
  val backend: String? = null,
)

/** The device a sweep ran on, so two devices' rows never pool by accident. */
@Serializable
data class EvalHardware(
  val platform: String? = null,
  val os: String? = null,
  @SerialName("os_version") val osVersion: String? = null,
  val device: String? = null,
  val cpu: String? = null,
  @SerialName("ram_gb") val ramGb: Double? = null,
  @SerialName("app_version") val appVersion: String? = null,
  @SerialName("api_level") val apiLevel: Int? = null,
  val key: String? = null,
) {
  /**
   * A stable grouping key when a row does not carry one. Legacy rows written
   * before `key` existed still group with new rows because the key is derived
   * with the same rule the app uses when it writes the field, not with a second
   * spelling of the same hardware.
   */
  fun keyOrDerived(): String = key?.takeIf { it.isNotBlank() } ?: DeviceInfo(
    platform = platform,
    os = os,
    osVersion = osVersion,
    device = device,
    cpu = cpu,
    ramGb = ramGb,
  ).key()
}

/** Parameters that describe the sweep rather than any one cell. */
@Serializable
data class EvalRunMeta(
  val id: String,
  @SerialName("started_at") val startedAt: String? = null,
  @SerialName("base_url") val baseUrl: String? = null,
  val runs: Int = 1,
  @SerialName("num_ctx") val numCtx: Int? = null,
  val temperature: Double? = null,
  @SerialName("load_timeout") val loadTimeout: Double? = null,
  val script: String? = null,
  val hardware: EvalHardware = EvalHardware(),
)

/**
 * One self-describing row per `(model, run)`, flushed after the warmup and after
 * every cell. A process killed by the OOM killer therefore leaves a readable
 * partial row with `inProgress = true`, which the next launch can resume.
 */
@Serializable
data class EvalRow(
  val schema: Int = EvalFixtures.Schema,
  @SerialName("fixtures_version") val fixturesVersion: Int = EvalFixtures.Version,
  @SerialName("model_id") val modelId: String,
  val model: EvalModelMeta = EvalModelMeta(),
  val run: EvalRunMeta,
  val warmup: EvalWarmup? = null,
  val cells: List<EvalCell> = emptyList(),
  @SerialName("started_at") val startedAt: String? = null,
  @SerialName("finished_at") val finishedAt: String? = null,
  @SerialName("in_progress") val inProgress: Boolean = true,
  @SerialName("device_key") val deviceKey: String? = null,
) {
  /** The hardware group this row belongs to, derived when the field is absent. */
  fun deviceKeyOrDerived(): String =
    deviceKey?.takeIf { it.isNotBlank() } ?: run.hardware.keyOrDerived()
}

/** The one JSON codec used for row files and the LAN upload body. */
object EvalRowsJson {
  val json: Json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
    explicitNulls = true
  }

  fun encode(row: EvalRow): String = json.encodeToString(row)

  fun decode(text: String): EvalRow? = runCatching { json.decodeFromString<EvalRow>(text) }.getOrNull()
}
