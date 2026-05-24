package com.pforhan.alphathinker.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pforhan.alphathinker.navigation.AppNavGraph
import com.pforhan.alphathinker.ui.theme.AlphaThinkerTheme
import com.pforhan.alphathinker.ui.theme.DarkTheme
import com.pforhan.alphathinker.ui.theme.LightTheme

class MainActivity : ComponentActivity() {

    private val app: AlphaThinkerApp
        get() = application as AlphaThinkerApp

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(app.repository)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlphaThinkerTheme(darkTheme = false) {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        viewModel = viewModel,
                        navController = androidx.navigation.compose.rememberNavController()
                    )
                }
            }
        }
    }
}
