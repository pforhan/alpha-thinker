package alphainterplanetary.thinker.eval

import kotlinx.serialization.encodeToString

/** The outcome of one LAN share, in words the UI can show without unwrapping. */
data class EvalUploadResult(val ok: Boolean, val message: String)

/**
 * Sends stored rows to the computer-side receiver (`tools/eval_receiver.py`)
 * over the local network.
 *
 * The body is a JSON array of the same self-describing row objects the app
 * writes to disk, so the receiver can verify and file them exactly as they were
 * measured. Sharing is deliberately explicit: nothing leaves the device until
 * the user names a receiver.
 */
object EvalRemote {
  /** The `/evals` endpoint for [baseUrl], accepting a bare host or a full URL. */
  fun url(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val withoutTrailing = withScheme.trimEnd('/')
    return if (withoutTrailing.endsWith("/evals")) withoutTrailing else "$withoutTrailing/evals"
  }

  suspend fun post(baseUrl: String, rows: List<EvalRow>): EvalUploadResult {
    if (rows.isEmpty()) return EvalUploadResult(false, "No evaluation rows to share yet.")
    val body = EvalRowsJson.json.encodeToString(rows)
    return postEvalJson(url(baseUrl), body)
  }
}

/** The platform's HTTP POST, or an unsupported result where there is no client. */
expect suspend fun postEvalJson(url: String, body: String): EvalUploadResult
