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

  override fun pathFor(artifactId: String, extension: String): String =
    "$prefix$artifactId.$extension"

  override suspend fun writeText(path: String, content: String): Result<Unit> = runCatching {
    window.localStorage.setItem(path, content)
  }

  override suspend fun readText(path: String): String? = runCatching {
    window.localStorage.getItem(path)
  }.getOrNull()

  override suspend fun delete(path: String): Boolean = runCatching {
    val existing = window.localStorage.getItem(path)
    window.localStorage.removeItem(path)
    true.also { /* absent or removed both count as success */ }
  }.getOrDefault(true)
}
