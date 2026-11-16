package alphainterplanetary.thinker.di

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** iOS platform: the database is a SQLite file in the app's Documents directory. */
@OptIn(ExperimentalForeignApi::class)
class IosPlatformContext : PlatformContext {
  override fun databaseFile(name: String): String {
    val dir = NSFileManager.defaultManager.URLsForDirectory(
      NSDocumentDirectory,
      NSUserDomainMask,
    ).first() as? NSURL ?: error("Could not resolve Documents directory")
    return dir.path + "/$name"
  }

  override fun exportsDirectoryPath(): String {
    val dir = NSFileManager.defaultManager.URLsForDirectory(
      NSApplicationSupportDirectory,
      NSUserDomainMask,
    ).first() as? NSURL
    val base = dir?.path ?: error("Could not resolve Application Support directory")
    val exports = "$base/exports"
    NSFileManager.defaultManager.createDirectoryAtPath(
      exports,
      withIntermediateDirectories = true,
      attributes = null,
      error = null,
    )
    return exports
  }
}