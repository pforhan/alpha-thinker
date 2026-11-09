package alphainterplanetary.thinker.di

import java.io.File

/** Desktop (JVM) platform: the database is a SQLite file under the user's home directory. */
class DesktopPlatformContext : PlatformContext {
  private val home: File
    get() = File(System.getProperty("user.home"), ".alphathinker")

  override fun databaseFile(name: String): String {
    val dir = home
    dir.mkdirs()
    return File(dir, name).absolutePath
  }
}
