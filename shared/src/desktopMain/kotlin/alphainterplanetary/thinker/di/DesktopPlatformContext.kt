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

  override fun modelDirectory(): String = File(home, "models").apply { mkdirs() }.absolutePath

  override fun fileExists(path: String): Boolean = File(path).isFile

  override fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }
    .getOrDefault(false) || !File(path).exists()
}
