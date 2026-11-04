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
  /**
   * The receiver's self-signed certificate, checked in at
   * `tools/eval_tls/cert.pem` and pinned by every HTTPS connection this object
   * makes. Because the certificate is pinned by its public key, the receiver
   * cannot be impersonated by another server on the same Wi-Fi.
   *
   * The private key is checked in next to it: this is a shared development
   * certificate for a tool that only runs on the user's own LAN, not a secret.
   */
  val pinnedCertificate: String = """
    -----BEGIN CERTIFICATE-----
    MIIDSjCCAjKgAwIBAgIUC8tEZ7Mzzxtze0f+I2qxt1vpe1IwDQYJKoZIhvcNAQEL
    BQAwJjEkMCIGA1UEAwwbQWxwaGEgVGhpbmtlciBFdmFsIFJlY2VpdmVyMB4XDTI2
    MTEwNDE1MzQ1NloXDTM2MTEwMTE1MzQ1NlowJjEkMCIGA1UEAwwbQWxwaGEgVGhp
    bmtlciBFdmFsIFJlY2VpdmVyMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
    AQEAm4TwmzTVN+aYEbBcBYBMsIaBgevIb/YQoYk3DIzxAAQnEZw9VVrgEsw5nf+n
    cMWA2rrf1slKt3TpFxIl5NDSPtuxjdW1Am4ChtqYD3vSZGeLEUYBIaianH2khMP0
    5IGjDJtFsKPzAnsmn03/VqohWNvVaDxuIHt+VEhq9H7lvfkIovV/Z20eEk3RZC0V
    RQlRI52NodJP//+t5rUrpvrodupV4+c/5TTqHFZ/C0RA1ONIq9wakV962XGbABJ1
    XmNxwXnxkxvzLYBVaqTdqJ8sTq44XeFTxWAf9XdgX2wTTiHfu3GB+GeUFx74/Yc1
    vGydD5vqi7gBxKoYHmRQfYSyXQIDAQABo3AwbjAdBgNVHQ4EFgQUP4HYfhxsRinp
    QaJPk5RaaYZQY/4wGgYDVR0RBBMwEYIJbG9jYWxob3N0hwR/AAABMAwGA1UdEwEB
    /wQCMAAwDgYDVR0PAQH/BAQDAgWgMBMGA1UdJQQMMAoGCCsGAQUFBwMBMA0GCSqG
    SIb3DQEBCwUAA4IBAQCASTN+Q5lOJDRKgizklKFpwYBlJGd6I0TVE52Ebz2Ri0Ct
    GMnbCqBioEoF8NKjuc1XII/LKAgtGof/5zx4J+ktf6vfvaAfjZ20YVcgRcIJ7Vri
    2AhPD8NKdqFufuX6LLFgou6R2shimB5W+CTCMYBhT7rtpCz6J1+AJOuYOTjcTj72
    oJ6NS5Ur37n5kDlQDHl/NuAmOLhPsPrRZI0faxGyNV+Tpja1QmQY4eHZMMMC9aZn
    T/LM5Htr/QNbvSvDR7WdHEvUkauXwHUdjHlNnVrVbmzfFJtWEcLqMRnzyrWAGc5R
    Cm06LtXKpvEGZCYADGEArRd1wdVcbGkNYLOJv0o8
    -----END CERTIFICATE-----
  """.trimIndent()

  /**
   * The `/evals` endpoint for [baseUrl], accepting a bare host or a full URL.
   *
   * A bare host defaults to `https://`. An explicit `http://` is rejected
   * instead of silently upgraded: the user needs to know the receiver is not
   * the TLS one, because falling back to cleartext would expose every raw
   * model reply on the local network.
   */
  fun url(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
    require(withScheme.startsWith("https://", ignoreCase = true)) {
      "The eval receiver must use https://; rows are never sent over plain HTTP."
    }
    val withoutTrailing = withScheme.trimEnd('/')
    return if (withoutTrailing.endsWith("/evals")) withoutTrailing else "$withoutTrailing/evals"
  }

  suspend fun post(baseUrl: String, rows: List<EvalRow>): EvalUploadResult {
    if (rows.isEmpty()) return EvalUploadResult(false, "No evaluation rows to share yet.")
    val target = try {
      url(baseUrl)
    } catch (e: IllegalArgumentException) {
      return EvalUploadResult(false, e.message ?: "That receiver address is not valid.")
    }
    val body = EvalRowsJson.json.encodeToString(rows)
    return postEvalJson(target, body)
  }
}

/** The platform's HTTP POST, or an unsupported result where there is no client. */
expect suspend fun postEvalJson(url: String, body: String): EvalUploadResult
