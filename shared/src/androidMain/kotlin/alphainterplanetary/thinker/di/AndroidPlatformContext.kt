package alphainterplanetary.thinker.di

import android.content.Context
import java.io.File

class AndroidPlatformContext(androidContext: Context) : PlatformContext {
  val appContext: Context = androidContext.applicationContext

  override fun databaseFile(name: String): String = appContext.getDatabasePath(name).absolutePath

  override fun exportsDirectoryPath(): String =
    File(appContext.filesDir, "exports").apply { mkdirs() }.absolutePath
}
