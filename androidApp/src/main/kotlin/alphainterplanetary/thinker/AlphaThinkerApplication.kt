package alphainterplanetary.thinker

import android.app.Application
import alphainterplanetary.thinker.database.initDatabase

class AlphaThinkerApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    initDatabase(this)
  }
}
