package alphainterplanetary.thinker.engine

/**
 * A model the Local tier can install in-process (MODEL-CATALOG-PLAN.md stage 3).
 *
 * The catalog is deliberately small and curated: every [downloadUrl] resolves
 * directly to one `.litertlm` file, so a download is a single transfer followed
 * by a checksum check — no archive extraction and no model conversion. [id] is
 * the catalog identity (the Hugging Face repo id), while [fileName] is what
 * LiteRT resolves against its model directory; a downloaded
 * [EngineInstance] stores both in its `model` and `localPath` fields.
 */
data class LocalModel(
  /** Stable catalog id, also the repo id the file came from. */
  val id: String,
  val label: String,
  val description: String,
  val repoId: String,
  /** The `.litertlm` file name LiteRT opens. */
  val fileName: String,
  val downloadUrl: String,
  /** Size Hugging Face reports, used for disk-space checks and progress. */
  val expectedBytes: Long,
  /** Lowercase hex SHA-256 of the completed file. */
  val sha256: String,
  val contextTokens: Int,
  val license: String,
  /**
   * Weight count and quantization when the catalog knows them. They drive the
   * evaluation harness's footprint estimate and its report; a model whose file
   * is already measured by [expectedBytes] can leave them null.
   */
  val parameterCount: Long? = null,
  val quantization: String? = null,
)

/** The file name LiteRT will look up under the model directory. */
val LocalModel.fileExtension: String
  get() = fileName.substringAfterLast('.', missingDelimiterValue = "")
