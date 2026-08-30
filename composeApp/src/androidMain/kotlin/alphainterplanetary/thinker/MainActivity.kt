package alphainterplanetary.thinker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import alphainterplanetary.thinker.database.initDatabase

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initDatabase(applicationContext)
    setContent {
      App()
    }
  }
}