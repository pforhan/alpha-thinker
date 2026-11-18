package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.browser.window

actual fun provideExportFileStore(platformContext: PlatformContext): ExportFileStore =
  WasmExportFileStore()

/**
 * Browser fallback "managed file" store backed by localStorage. Paths are used
 * as stable keys under an app-private prefix; this provides persistent,
 * app-private storage in environments without direct filesystem access.
 */
private class WasmExportFileStore : ExportFileStore {
  private val prefix = "alphathinker.export."

  override fun filenameFor(artifactId: String, extension: String): String =
    "$artifactId.$extension"

  override fun resolve(ref: String): String =
    if (ref.startsWith(prefix)) ref else "$prefix$ref"

  override suspend fun writeText(ref: String, content: String): Result<Unit> = runCatching {
    window.localStorage.setItem(resolve(ref), content)
  }

  override suspend fun readText(ref: String): String? = runCatching {
    window.localStorage.getItem(resolve(ref))
  }.getOrNull()

  override suspend fun delete(ref: String): Boolean = runCatching {
    window.localStorage.removeItem(resolve(ref))
    true.also { /* absent or removed both count as success */ }
  }.getOrDefault(true)
}
