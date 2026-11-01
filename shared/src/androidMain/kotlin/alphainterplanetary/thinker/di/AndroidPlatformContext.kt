package alphainterplanetary.thinker.di

import android.content.Context
import java.io.File

class AndroidPlatformContext(androidContext: Context) : PlatformContext {
  private val context: Context = androidContext.applicationContext

  override fun databaseFile(name: String): String = context.getDatabasePath(name).absolutePath

  /**
   * Internal app storage, not external: downloaded models must survive a USB
   * unmount and must not need a storage permission. The directory is created on
   * first read because the download client writes into it directly.
   */
  override fun modelDirectory(): String = File(context.filesDir, "models").apply { mkdirs() }.absolutePath

  override fun fileExists(path: String): Boolean = File(path).isFile

  override fun deleteFile(path: String): Boolean = runCatching { File(path).delete() }
    .getOrDefault(false) || !File(path).exists()
}
