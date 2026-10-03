package alphainterplanetary.thinker.di

import android.content.Context

class AndroidPlatformContext(androidContext: Context) : PlatformContext {
  private val context: Context = androidContext.applicationContext

  override fun databaseFile(name: String): String = context.getDatabasePath(name).absolutePath
}