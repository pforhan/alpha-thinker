package alphainterplanetary.thinker.eval

import alphainterplanetary.thinker.di.PlatformContext

/**
 * The on-disk half of the harness: one self-describing JSON row per
 * `(model, run)` under [PlatformContext.evalDirectory].
 *
 * The shape is the same file-as-row layout `tools/eval_ollama_models.py` writes,
 * so a row recorded on a phone can be dropped into `docs/model-evals` next to
 * the desktop rows and read by the same report. Writing after every request is
 * what makes a sweep killable: the row left behind has `in_progress: true`, the
 * warmup, and every cell that already landed.
 */
class EvalRowStore(
  private val platform: PlatformContext,
) {
  fun directory(): String? = platform.evalDirectory()

  /** The file name a row is stored under, mirroring the Python harness's naming. */
  fun fileName(row: EvalRow): String = fileName(row.modelId, row.run.id)

  fun fileName(modelId: String, runId: String): String = "${slugify(modelId)}-$runId.json"

  fun save(row: EvalRow): Boolean {
    val directory = directory() ?: return false
    return platform.writeTextFile("${directory.trimEnd('/')}/${fileName(row)}", EvalRowsJson.encode(row))
  }

  /** Every readable row in the eval directory, oldest file name first. */
  fun loadAll(): List<EvalRow> {
    val directory = directory() ?: return emptyList()
    return platform.listFiles(directory)
      .filter { it.endsWith(".json") }
      .sorted()
      .mapNotNull { name -> platform.readTextFile("${directory.trimEnd('/')}/$name") }
      .mapNotNull(EvalRowsJson::decode)
  }

  fun delete(row: EvalRow): Boolean {
    val directory = directory() ?: return false
    return platform.deleteFile("${directory.trimEnd('/')}/${fileName(row)}")
  }

  companion object {
    /** The Python harness's `slugify`: keep the identity readable in a file name. */
    fun slugify(modelId: String): String =
      modelId.replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "model" }
  }
}
