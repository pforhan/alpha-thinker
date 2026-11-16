package alphainterplanetary.thinker.di

import android.content.Context

class AndroidPlatformContext(androidContext: Context) : PlatformContext {
  val appContext: Context = androidContext.applicationContext

  override fun databaseFile(name: String): String = appContext.getDatabasePath(name).absolutePath
}
