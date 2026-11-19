package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlin.io.encoding.Base64
import kotlinx.browser.window

actual fun provideExportFileStore(platformContext: PlatformContext): ExportFileStore =
  WasmExportFileStore()

/**
 * Browser fallback "managed file" store backed by localStorage. Paths are used
 * as stable keys under an app-private prefix; this provides persistent,
 * app-private storage in environments without direct filesystem access.
 *
 * Binary-safe: bytes are stored as base64 (prefixed) so compressed interchange
 * exports work. Legacy plain-text entries (pre-bytes) are still readable.
 */
private class WasmExportFileStore : ExportFileStore {
  private val prefix = "alphathinker.export."
  private val b64Prefix = "b64:"

  override fun filenameFor(artifactId: String, extension: String): String =
    "$artifactId.$extension"

  override fun resolve(ref: String): String =
    if (ref.startsWith(prefix)) ref else "$prefix$ref"

  override suspend fun writeBytes(ref: String, bytes: ByteArray): Result<Unit> = runCatching {
    window.localStorage.setItem(resolve(ref), b64Prefix + Base64.encode(bytes))
  }

  override suspend fun readBytes(ref: String): ByteArray? = runCatching {
    val stored = window.localStorage.getItem(resolve(ref)) ?: return@runCatching null
    if (stored.startsWith(b64Prefix)) {
      Base64.decode(stored.removePrefix(b64Prefix))
    } else {
      // Legacy: value was stored as plain UTF-8 text.
      stored.encodeToByteArray()
    }
  }.getOrNull()

  override suspend fun delete(ref: String): Boolean = runCatching {
    window.localStorage.removeItem(resolve(ref))
    /* absent or removed both count as success */
    true
  }.getOrDefault(true)
}
