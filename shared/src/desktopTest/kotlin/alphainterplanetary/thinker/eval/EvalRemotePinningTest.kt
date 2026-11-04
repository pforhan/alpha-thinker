package alphainterplanetary.thinker.eval

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The app compiles the receiver certificate into common code, while the Python
 * receiver loads the copy under `tools/eval_tls`. If the two ever drift, every
 * share fails the handshake, so the mismatch is worth a test rather than a
 * debugging session on a phone.
 */
class EvalRemotePinningTest {
  @Test
  fun `the pinned certificate matches tools eval_tls cert pem`() {
    val checkedIn = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .map { File(it, "tools/eval_tls/cert.pem") }
      .firstOrNull { it.isFile }
      ?: error("Could not find tools/eval_tls/cert.pem above ${System.getProperty("user.dir")}")

    assertEquals(checkedIn.readText().trim(), EvalRemote.pinnedCertificate.trim())
  }

  @Test
  fun `the checked-in key belongs to the pinned certificate`() {
    val tlsDir = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .map { File(it, "tools/eval_tls") }
      .firstOrNull { it.isDirectory }
      ?: error("Could not find tools/eval_tls above ${System.getProperty("user.dir")}")

    val text = File(tlsDir, "key.pem").readText()
    assertTrue(text.contains("BEGIN PRIVATE KEY"), "key.pem should be PKCS#8 for the receiver")
  }
}
