package alphainterplanetary.thinker.eval

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EvalRemoteTest {
  @Test
  fun `a bare host defaults to https`() {
    assertEquals(
      "https://192.168.1.20:8765/evals",
      EvalRemote.url("192.168.1.20:8765"),
    )
  }

  @Test
  fun `an https url keeps its scheme and gets the evals path once`() {
    assertEquals(
      "https://receiver.local:8765/evals",
      EvalRemote.url("https://receiver.local:8765/"),
    )
    assertEquals(
      "https://receiver.local/evals",
      EvalRemote.url("https://receiver.local/evals"),
    )
  }

  @Test
  fun `an explicit http url is rejected`() {
    val error = assertFailsWith<IllegalArgumentException> {
      EvalRemote.url("http://192.168.1.20:8765")
    }
    assertTrue(error.message.orEmpty().contains("https"))
  }

  @Test
  fun `posting to a plain http receiver fails without sending`() = runTest {
    val result = EvalRemote.post("http://192.168.1.20:8765", listOf(sampleRow()))

    assertFalse(result.ok)
    assertTrue(result.message.contains("https"))
  }

  @Test
  fun `posting with nothing measured is reported without a request`() = runTest {
    val result = EvalRemote.post("192.168.1.20:8765", emptyList())

    assertFalse(result.ok)
    assertTrue(result.message.contains("No evaluation rows"))
  }

  @Test
  fun `the pinned certificate is the checked-in receiver certificate`() {
    // The desktop test also compares this constant with tools/eval_tls/cert.pem;
    // this keeps the shape intact on every platform.
    assertTrue(EvalRemote.pinnedCertificate.startsWith("-----BEGIN CERTIFICATE-----"))
    assertTrue(EvalRemote.pinnedCertificate.endsWith("-----END CERTIFICATE-----"))
  }

  private fun sampleRow(): EvalRow = EvalRow(
    modelId = "warped-community/Llama-3.2-3B-litert-lm",
    run = EvalRunMeta(id = "run-1", runs = 1),
    inProgress = true,
  )
}
