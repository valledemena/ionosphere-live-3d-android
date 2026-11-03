package com.valledemena.ionosphere

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.valledemena.ionosphere.ui.GlobeScreen

/**
 * Una sola actividad, sin navegación y sin pantallas inventadas: R1 es esqueleto.
 * El HUD y los paneles llegan en R3 (`IMPLEMENTATION_SLICES.md`).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    GlobeScreen()
                }
            }
        }
    }
}
