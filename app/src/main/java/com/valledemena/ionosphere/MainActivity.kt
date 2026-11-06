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
import com.valledemena.ionosphere.render.AtmoShaderConstants
import com.valledemena.ionosphere.ui.GlobeScreen

/**
 * Una sola actividad, sin navegación y sin pantallas inventadas: R1 es esqueleto.
 * El HUD y los paneles llegan en R3 (`IMPLEMENTATION_SLICES.md`).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // R3.2b-iii rider (K1/K2): overrides de pasos por extras de lanzamiento,
        // resueltos solo aqui y solo si caen en el rango de los pins; fuera de
        // rango o ausentes el renderer queda en los defaults (12/4). Sin UI,
        // sin persistencia, sin clamp.
        val atmoSteps = intent.getIntExtra("atmoSteps", -1)
            .takeIf { it in AtmoShaderConstants.STEPS_MIN..AtmoShaderConstants.STEPS_MAX }
        val atmoLightSteps = intent.getIntExtra("atmoLightSteps", -1)
            .takeIf { it in AtmoShaderConstants.LIGHT_STEPS_MIN..AtmoShaderConstants.LIGHT_STEPS_MAX }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    GlobeScreen(atmoSteps = atmoSteps, atmoLightSteps = atmoLightSteps)
                }
            }
        }
    }
}
