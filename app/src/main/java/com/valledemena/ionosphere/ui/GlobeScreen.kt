package com.valledemena.ionosphere.ui

import androidx.compose.foundation.layout.Box
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
 * El ciclo de vida manda:
 *  - `ON_RESUME` reanuda el contexto GL; `ON_PAUSE` lo suspende
 *    (con `preserveEGLContextOnPause` el contexto sobrevive al background).
 *  - `ON_START` empieza a pedir frames a la rejilla de vsync; `ON_STOP` los
 *    corta.
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
                    Lifecycle.Event.ON_RESUME -> surfaceView.onResume()
                    Lifecycle.Event.ON_PAUSE -> surfaceView.onPause()
                    Lifecycle.Event.ON_START -> surfaceView.start()
                    Lifecycle.Event.ON_STOP -> surfaceView.stop()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                surfaceView.onResume()
                surfaceView.start()
            } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                surfaceView.start()
            }
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                surfaceView.stop()
                surfaceView.onPause()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> GlobeSurfaceView(context).also { view = it } },
        )
        // Panel de diagnostico (debug-only; en release dataPanelFactory es null
        // y este call es un no-op - ver DataPanelHost.kt).
        DataPanelHost()
    }
}
