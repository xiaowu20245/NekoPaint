package io.github.xiaowu20245.nekopaint

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.xiaowu20245.nekopaint.core.ui.NekoPaintApp
import io.github.xiaowu20245.nekopaint.core.ui.theme.NekoPaintTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NekoPaintTheme {
                NekoPaintApp()
            }
        }
    }
}
