package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  IosPlatformExportActions()

private class IosPlatformExportActions : PlatformExportActions {
  override suspend fun save(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): String? = null // User-chosen save/export picker requires UIKit integration; degrade gracefully.

  override suspend fun share(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = false // Share sheet (UIActivityViewController) requires UI integration; degrade gracefully.

  override suspend fun open(
    filename: String,
    mimeType: String,
    internalPath: String,
    content: String,
  ): Boolean = false
}
