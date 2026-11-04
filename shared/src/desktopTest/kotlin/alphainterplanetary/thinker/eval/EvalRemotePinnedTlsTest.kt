package alphainterplanetary.thinker.eval

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import java.io.File
import java.net.InetSocketAddress
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The desktop client performs a real handshake against the checked-in receiver
 * certificate, so a mistake in the pinned trust store fails here rather than as
 * an opaque "Could not reach" on a phone.
 */
class EvalRemotePinnedTlsTest {
  @Test
  fun `posting succeeds against the pinned certificate`() = runTest {
    val tlsDir = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .map { File(it, "tools/eval_tls") }
      .firstOrNull { it.isDirectory }
      ?: error("Could not find tools/eval_tls above ${System.getProperty("user.dir")}")

    val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.httpsConfigurator = HttpsConfigurator(serverContext(tlsDir))
    server.createContext("/evals") { exchange ->
      exchange.requestBody.readBytes()
      val payload = "stored".toByteArray()
      exchange.sendResponseHeaders(200, payload.size.toLong())
      exchange.responseBody.use { it.write(payload) }
    }
    server.start()
    try {
      val port = server.address.port
      val result = postEvalJson("https://127.0.0.1:$port/evals", "[]")

      assertTrue(result.ok, result.message)
      assertEquals("stored", result.message)
    } finally {
      server.stop(0)
    }
  }

  private fun serverContext(tlsDir: File): SSLContext {
    val certificate = CertificateFactory.getInstance("X.509")
      .generateCertificate(File(tlsDir, "cert.pem").inputStream()) as X509Certificate
    val key = KeyFactory.getInstance("RSA").generatePrivate(
      PKCS8EncodedKeySpec(
        Base64.getMimeDecoder().decode(
          File(tlsDir, "key.pem").readText()
            .substringAfter("-----BEGIN PRIVATE KEY-----")
            .substringBefore("-----END PRIVATE KEY-----"),
        ),
      ),
    )
    val password = "eval".toCharArray()
    val store = KeyStore.getInstance("PKCS12").apply {
      load(null, null)
      setKeyEntry("receiver", key, password, arrayOf(certificate))
    }
    val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
      init(store, password)
    }
    return SSLContext.getInstance("TLS").apply {
      init(managers.keyManagers, null, null)
    }
  }
}
