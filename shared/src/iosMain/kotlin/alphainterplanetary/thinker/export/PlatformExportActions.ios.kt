package alphainterplanetary.thinker.export

import alphainterplanetary.thinker.di.PlatformContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

actual fun providePlatformExportActions(platformContext: PlatformContext): PlatformExportActions =
  IosPlatformExportActions()

private class IosPlatformExportActions : PlatformExportActions {
  @OptIn(ExperimentalForeignApi::class)
  override suspend fun save(filename: String, mimeType: String, content: String): String? =
    runCatching {
      val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
      val dir = (paths.firstOrNull() as? String) ?: return null
      val exportsDir = "$dir/exports"
      NSFileManager.defaultManager.createDirectoryAtPath(
        exportsDir,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
      )
      val path = "$exportsDir/$filename"
      @Suppress("CAST_NEVER_SUCCEEDS")
      (content as NSString).writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
      path
    }.getOrNull()

  override suspend fun share(filename: String, mimeType: String, content: String): Boolean =
    save(filename, mimeType, content) != null
}
