package alphainterplanetary.thinker.di

import java.io.File

/** Desktop (JVM) platform: the database is a SQLite file under the user's home directory. */
class DesktopPlatformContext : PlatformContext {
  override fun databaseFile(name: String): String {
    val dir = File(System.getProperty("user.home"), ".alphathinker")
    dir.mkdirs()
    return File(dir, name).absolutePath
  }
}