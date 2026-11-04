package alphainterplanetary.thinker.eval

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun postEvalJson(url: String, body: String): EvalUploadResult =
  withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
      connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 10_000
        readTimeout = 30_000
        doOutput = true
        setRequestProperty("Content-Type", "application/json; charset=utf-8")
        setRequestProperty("Accept", "application/json")
      }
      connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      val code = connection.responseCode
      val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
        ?.bufferedReader()
        ?.use { it.readText() }
        .orEmpty()
      if (code in 200..299) {
        EvalUploadResult(
          true,
          response.trim().takeIf { it.isNotEmpty() } ?: "Shared to the receiver (HTTP $code).",
        )
      } else {
        EvalUploadResult(false, "Receiver returned HTTP $code${response.shortBody()}")
      }
    } catch (e: IOException) {
      EvalUploadResult(false, "Could not reach $url: ${e.message ?: e::class.simpleName}")
    } catch (e: Exception) {
      EvalUploadResult(false, "Share failed: ${e.message ?: e::class.simpleName}")
    } finally {
      connection?.disconnect()
    }
  }

private fun String.shortBody(): String {
  val trimmed = trim()
  return if (trimmed.isEmpty()) "" else ": ${trimmed.take(300)}"
}
