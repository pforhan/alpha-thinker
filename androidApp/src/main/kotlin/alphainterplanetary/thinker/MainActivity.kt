package alphainterplanetary.thinker

import alphainterplanetary.thinker.di.AndroidPlatformContext
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val platformContext = remember { AndroidPlatformContext(applicationContext) }
      App(platformContext = platformContext)
    }
  }
}