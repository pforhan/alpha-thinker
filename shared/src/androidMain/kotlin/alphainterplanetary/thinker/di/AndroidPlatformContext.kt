package alphainterplanetary.thinker.di

import android.content.Context

class AndroidPlatformContext(androidContext: Context) : PlatformContext {
  val context: Context = androidContext.applicationContext
}