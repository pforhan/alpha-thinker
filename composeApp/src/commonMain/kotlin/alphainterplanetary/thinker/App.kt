package alphainterplanetary.thinker

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import alphainterplanetary.thinker.ui.navigation.NavGraph

@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier,
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            NavGraph(
                navController = navController,
                onProjectCreated = { }
            )
        }
    }
}
