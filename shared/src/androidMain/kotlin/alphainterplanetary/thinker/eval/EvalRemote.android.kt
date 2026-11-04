package alphainterplanetary.thinker.eval

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
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
      (connection as? HttpsURLConnection)?.let { https ->
        https.sslSocketFactory = pinnedSocketFactory
        // The pinned certificate's name cannot match every LAN address a user
        // might type in, so this connection proves identity with the pinned key
        // instead of the host string. It is scoped to this one connection.
        https.hostnameVerifier = HostnameVerifier { _, _ -> true }
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

/**
 * A trust store holding exactly the receiver certificate and nothing else, so
 * any other certificate — including a valid public one — fails the handshake.
 */
private val pinnedSocketFactory: SSLSocketFactory by lazy {
  val certificate = CertificateFactory.getInstance("X.509")
    .generateCertificate(EvalRemote.pinnedCertificate.byteInputStream()) as X509Certificate
  val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
    load(null, null)
    setCertificateEntry("alpha-thinker-eval-receiver", certificate)
  }
  val managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    .apply { init(store) }
    .trustManagers
  SSLContext.getInstance("TLS").apply {
    init(null, managers, null)
  }.socketFactory
}

private fun String.shortBody(): String {
  val trimmed = trim()
  return if (trimmed.isEmpty()) "" else ": ${trimmed.take(300)}"
}
