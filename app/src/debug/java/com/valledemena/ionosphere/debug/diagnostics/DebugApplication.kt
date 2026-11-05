package com.valledemena.ionosphere.debug.diagnostics

import android.app.Application
import com.valledemena.ionosphere.data.DataTtl
import com.valledemena.ionosphere.data.net.InMemoryCache
import com.valledemena.ionosphere.data.net.OkHttpHttpClient
import com.valledemena.ionosphere.data.source.AuroraAdapter
import com.valledemena.ionosphere.data.source.DstAdapter
import com.valledemena.ionosphere.data.source.SpaceWeatherAdapter
import com.valledemena.ionosphere.data.source.ionosondes.IonosondeAdapter
import com.valledemena.ionosphere.ui.dataPanelFactory

/**
 * Application que solo existe en el build debug (declarada en
 * `src/debug/AndroidManifest.xml`, sin `android:name` en main que la tape).
 * Registra el panel en el seam de `main` (contrato C3: main se queda inerte
 * en release porque aqui jamas se ejecuta).
 */
class DebugApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val http = OkHttpHttpClient()
        val cache = InMemoryCache()
        // Instancias vivas entre ciclos (el N2 in-memory de SpaceWeather
        // sobrevive a los refresh; F-N2 reintenta en el proximo ciclo).
        val aurora = AuroraAdapter(http, cache)
        val dst = DstAdapter(http, cache)
        val ionosonde = IonosondeAdapter(http, cache)
        val spaceWeather = SpaceWeatherAdapter(http, cache)
        dataPanelFactory = {
            DebugDataPanel(
                listOf(
                    RowSpec("Aurora", DataTtl.AURORA_MS) { aurora.fetch() },
                    RowSpec("Dst", DataTtl.DST_MS) { dst.fetch() },
                    RowSpec("Ionosonde", DataTtl.IONOSONDES_DATA_MS) { ionosonde.fetch() },
                    RowSpec("Space weather", DataTtl.SPACE_WEATHER_MS) { spaceWeather.fetch() },
                ),
            )
        }
    }
}
