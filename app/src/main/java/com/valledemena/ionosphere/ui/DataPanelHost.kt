package com.valledemena.ionosphere.ui

import androidx.compose.runtime.Composable

/**
 * Seam debug-only del panel de diagnostico de datos (R2, contrato C1-C8).
 *
 * En `main` vale `null` (inerte): `GlobeScreen` no compone nada y release no
 * lleva ni el panel ni camino de red. La app debug registra la lambda que
 * compone el panel desde su `Application` (`src/debug`). Sin registro = sin
 * panel: debug-only por construccion, verificable en release.
 */
@Volatile
var dataPanelFactory: (@Composable () -> Unit)? = null

@Composable
fun DataPanelHost() {
    val panel = dataPanelFactory
    if (panel != null) panel()
}
