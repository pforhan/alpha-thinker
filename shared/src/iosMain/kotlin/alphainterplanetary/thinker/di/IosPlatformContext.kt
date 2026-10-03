package alphainterplanetary.thinker.di

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** iOS platform: the database is a SQLite file in the app's Documents directory. */
class IosPlatformContext : PlatformContext {
  override fun databaseFile(name: String): String {
    val dir = NSFileManager.defaultManager.URLsForDirectory(
      NSDocumentDirectory,
      NSUserDomainMask,
    ).first() as? NSURL ?: error("Could not resolve Documents directory")
    return dir.path + "/$name"
  }
}