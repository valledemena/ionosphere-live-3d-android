package com.valledemena.ionosphere.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.valledemena.ionosphere.gl.GlobeSurfaceView

/**
 * Globo a pantalla completa, sin HUD (R3).
 *
 * El ciclo de vida manda: `ON_START` empieza a pedir frames, `ON_STOP` los corta.
 * Nada se dibuja con la app en background, y no hay ningún hilo propio: el
 * `Choreographer` del `GlobeSurfaceView` es el único que pide frames.
 */
@Composable
fun GlobeScreen(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var view by remember { mutableStateOf<GlobeSurfaceView?>(null) }

    DisposableEffect(lifecycleOwner, view) {
        val surfaceView = view
        if (surfaceView == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> surfaceView.start()
                    Lifecycle.Event.ON_STOP -> surfaceView.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                surfaceView.start()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                surfaceView.stop()
            }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context -> GlobeSurfaceView(context).also { view = it } },
    )
}
