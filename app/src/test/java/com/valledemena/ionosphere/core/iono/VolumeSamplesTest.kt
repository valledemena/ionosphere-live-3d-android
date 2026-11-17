package com.valledemena.ionosphere.core.iono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * G8 / P1-P2: espejo de `sampleVolume` (oraculo `DensityVolume.h:146-170`) y
 * de `extractPeakGrids` (oraculo `DensityVolume.h:76-125`).
 *
 * Los volumenes de prueba son sinteticos y deterministas; no se usa el modelo
 * Chapman aqui (eso ya lo cubre R4.4). Lo que se fija es la aritmetica del
 * muestreo: wrap/clamp, bracketing `^1.5`, orden de los lerps, argmax estricto,
 * Lagrange sobre alturas reales y umbral 8.5.
 */
class VolumeSamplesTest {

    private fun testVolume(
        lonRes: Int,
        latRes: Int,
        altRes: Int,
        altMinKm: Double = 60.0,
        altMaxKm: Double = 500.0,
        valid: Boolean = true,
        timestamp: Long = 0L,
        fill: (x: Int, y: Int, a: Int) -> Double,
    ): DensityVolume {
        val data = DoubleArray(lonRes * latRes * altRes)
        val layer = IntArray(lonRes * latRes * altRes)
        for (a in 0 until altRes) {
            for (y in 0 until latRes) {
                for (x in 0 until lonRes) {
                    data[volumeIndex(x, y, a, lonRes, latRes)] = fill(x, y, a)
                }
            }
        }
        return DensityVolume(
            lonRes = lonRes,
            latRes = latRes,
            altRes = altRes,
            altMinKm = altMinKm,
            altMaxKm = altMaxKm,
            data = data,
            layer = layer,
            valid = valid,
            timestamp = timestamp,
        )
    }

    /** Longitud del centro de la celda [x] en una rejilla de [w] columnas. */
    private fun cellLon(x: Int, w: Int): Float = (-180.0 + 360.0 * (x + 0.5) / w).toFloat()

    /** Latitud del centro de la fila [y] en una rejilla de [h] filas. */
    private fun cellLat(y: Int, h: Int): Float = (-90.0 + 180.0 * (y + 0.5) / h).toFloat()

    @Test
    fun `G8_1 sampleVolumeAt volumen invalido o degenerado devuelve 0`() {
        val invalid = testVolume(4, 4, 4, valid = false) { _, _, _ -> 0.5 }
        assertEquals(0.0f, sampleVolumeAt(invalid, 0.0f, 0.0f, 300.0f), 0.0f)

        val oneColumn = testVolume(1, 4, 4) { _, _, _ -> 0.5 }
        assertEquals(0.0f, sampleVolumeAt(oneColumn, 0.0f, 0.0f, 300.0f), 0.0f)

        val oneRow = testVolume(4, 1, 4) { _, _, _ -> 0.5 }
        assertEquals(0.0f, sampleVolumeAt(oneRow, 0.0f, 0.0f, 300.0f), 0.0f)

        val oneLevel = testVolume(4, 4, 1) { _, _, _ -> 0.5 }
        assertEquals(0.0f, sampleVolumeAt(oneLevel, 0.0f, 0.0f, 300.0f), 0.0f)

        val empty = DensityVolume(4, 4, 4, 60.0, 500.0, DoubleArray(0), IntArray(0), true)
        assertEquals(0.0f, sampleVolumeAt(empty, 0.0f, 0.0f, 300.0f), 0.0f)
    }

    @Test
    fun `G8_2 sampleVolumeAt en centros de celda devuelve el voxel exacto`() {
        val volume = testVolume(4, 4, 4) { x, y, a -> (x + 10 * y + 100 * a).toDouble() }
        val alt = volumeAltKmF(3, 4, 60.0f, 500.0f)
        assertEquals(321.0f, sampleVolumeAt(volume, cellLat(2, 4), cellLon(1, 4), alt), 0.0f)
        assertEquals(0.0f, sampleVolumeAt(volume, cellLat(0, 4), cellLon(0, 4), volumeAltKmF(0, 4, 60.0f, 500.0f)), 0.0f)
    }

    @Test
    fun `G8_3 sampleVolumeAt interpola a mitad de longitud`() {
        val volume = testVolume(4, 4, 4) { x, _, _ -> x.toDouble() }
        // Entre los centros x=1 (-45) y x=2 (45) la longitud es 0.
        assertEquals(1.5f, sampleVolumeAt(volume, 0.0f, 0.0f, 60.0f), 0.0f)
    }

    @Test
    fun `G8_4 sampleVolumeAt hace wrap en longitud en ambos bordes`() {
        val volume = testVolume(4, 4, 4) { x, _, _ -> x.toDouble() }
        // -180 cae entre la columna -1 (que envuelve a W-1) y la 0;
        // +180 cae entre W-1 y la 0 (que envuelve). Ambas deben dar lo mismo.
        val west = sampleVolumeAt(volume, 0.0f, -180.0f, 60.0f)
        val east = sampleVolumeAt(volume, 0.0f, 180.0f, 60.0f)
        assertEquals(1.5f, west, 0.0f)
        assertEquals(1.5f, east, 0.0f)
        // Consecuencia directa del wrap y no de un clamp: 0.5 * (3 + 0).
        assertEquals(0.5f * (3.0f + 0.0f), west, 0.0f)
    }

    @Test
    fun `G8_5 sampleVolumeAt clampa en latitud a las filas extremas`() {
        val volume = testVolume(4, 4, 4) { _, y, _ -> (10 * y).toDouble() }
        assertEquals(0.0f, sampleVolumeAt(volume, -90.0f, 0.0f, 60.0f), 0.0f)
        assertEquals(30.0f, sampleVolumeAt(volume, 90.0f, 0.0f, 60.0f), 0.0f)
        assertEquals(0.0f, sampleVolumeAt(volume, -200.0f, 0.0f, 60.0f), 0.0f)
        assertEquals(30.0f, sampleVolumeAt(volume, 200.0f, 0.0f, 60.0f), 0.0f)
    }

    @Test
    fun `G8_6 sampleVolumeAt clampa altitud fuera de rango y pinea niveles`() {
        val volume = testVolume(4, 4, 4) { _, _, a -> (100 * a).toDouble() }
        assertEquals(0.0f, sampleVolumeAt(volume, 0.0f, 0.0f, 0.0f), 0.0f)
        assertEquals(300.0f, sampleVolumeAt(volume, 0.0f, 0.0f, 2000.0f), 0.0f)
        assertEquals(200.0f, sampleVolumeAt(volume, 0.0f, 0.0f, volumeAltKmF(2, 4, 60.0f, 500.0f)), 0.0f)
    }

    @Test
    fun `G8_7 sampleVolumeAt interpola a mitad de altura entre niveles`() {
        val volume = testVolume(4, 4, 4) { _, _, a -> (100 * a).toDouble() }
        val h1 = volumeAltKmF(1, 4, 60.0f, 500.0f)
        val h2 = volumeAltKmF(2, 4, 60.0f, 500.0f)
        assertEquals(150.0f, sampleVolumeAt(volume, 0.0f, 0.0f, 0.5f * (h1 + h2)), 1e-4f)
    }

    @Test
    fun `G8_8 sampleVolumeAt usa los bounds del volumen`() {
        val volume = testVolume(4, 4, 4, altMinKm = 60.0, altMaxKm = 700.0) { _, _, a ->
            (100 * a).toDouble()
        }
        // Con techo 700 el nivel 3 esta a 700: si se usara 500 hardcodeado no
        // se llegaria al mismo valor de altura.
        assertEquals(700.0f, volumeAltKmF(3, 4, 60.0f, 700.0f), 1e-3f)
        assertEquals(300.0f, sampleVolumeAt(volume, 0.0f, 0.0f, 700.0f), 0.0f)
    }

    @Test
    fun `G8_9 sampleVolumeAt no muta el volumen`() {
        val volume = testVolume(4, 4, 4) { x, y, a -> (x + 10 * y + 100 * a).toDouble() }
        val before = volume.data.copyOf()
        sampleVolumeAt(volume, 12.5f, -33.25f, 321.0f)
        assertEquals(before.size, volume.data.size)
        before.indices.forEach { assertEquals(before[it], volume.data[it], 0.0) }
    }

    @Test
    fun `G8_9b sampleVolumeAt respeta el orden de los cuatro pesos`() {
        // gx = 1.25 y gy = 0.75 (fx != fy): un intercambio de pesos da otro valor.
        val volume = testVolume(4, 4, 4) { x, y, _ -> (x + 10 * y).toDouble() }
        val lon = (1.25 + 0.5) / 4.0 * 360.0 - 180.0
        val lat = (0.75 + 0.5) / 4.0 * 180.0 - 90.0
        // 0.75*0.25*1 + 0.25*0.25*2 + 0.75*0.75*11 + 0.25*0.75*12 = 8.75;
        // con fx y fy intercambiados daria 3.5.
        assertEquals(8.75f, sampleVolumeAt(volume, lat.toFloat(), lon.toFloat(), 60.0f), 1e-6f)
    }

    @Test
    fun `G8_10 extractPeakGrids afina con alturas reales no con indices`() {
        // Perfil simetrico y0 == y2: el vertice de Lagrange cae en (x0+x2)/2,
        // que NO es la altura del nivel bi por el espaciado ^1.5.
        val volume = testVolume(1, 1, 5) { _, _, a ->
            when (a) {
                0, 4 -> 0.2
                1, 3 -> 0.5
                else -> 0.6
            }
        }
        val grids = extractPeakGrids(volume)
        assertTrue(grids.valid)
        val x0 = volumeAltKmF(1, 5, 60.0f, 500.0f)
        val x1 = volumeAltKmF(2, 5, 60.0f, 500.0f)
        val x2 = volumeAltKmF(3, 5, 60.0f, 500.0f)
        val expected = 0.5f * (x0 + x2)
        assertEquals(expected, grids.hmKm[0], 1e-3f)
        assertTrue(expected - x1 > 10.0f)
        assertEquals(10.7f, grids.logNe[0], 1e-5f)
    }

    @Test
    fun `G8_11 extractPeakGrids umbral 8_5 deja hmKm a cero`() {
        val low = testVolume(1, 1, 5) { _, _, a ->
            when (a) {
                0, 4 -> 0.02
                1, 3 -> 0.1
                else -> 0.11
            }
        }
        val lowGrids = extractPeakGrids(low)
        assertEquals(8.495f, lowGrids.logNe[0], 1e-5f)
        assertEquals(0.0f, lowGrids.hmKm[0], 0.0f)

        val high = testVolume(1, 1, 5) { _, _, a ->
            when (a) {
                0, 4 -> 0.02
                1, 3 -> 0.1
                else -> 0.12
            }
        }
        val highGrids = extractPeakGrids(high)
        assertEquals(8.54f, highGrids.logNe[0], 1e-5f)
        assertTrue(highGrids.hmKm[0] > 0.0f)
    }

    @Test
    fun `G8_12 extractPeakGrids empate estricto se queda con el indice menor`() {
        val volume = testVolume(1, 1, 3) { _, _, a -> if (a == 0) 0.5 else if (a == 1) 0.5 else 0.1 }
        val grids = extractPeakGrids(volume)
        // bi = 0 (empate con a=1 resuelto a favor del menor): sin refinamiento,
        // hm = altMin. Con `>=` el mutante refinaria y daria otra altura.
        assertEquals(60.0f, grids.hmKm[0], 0.0f)
        assertEquals(10.25f, grids.logNe[0], 1e-5f)
    }

    @Test
    fun `G8_13 extractPeakGrids con alturas degeneradas no divide por cero`() {
        // altMin == altMax => x0 == x1 == x2 => den == 0: la guarda 1e-9 debe
        // dejar el pico en el vertice sin refinar (un mutante sin guarda da NaN).
        val volume = testVolume(1, 1, 5, altMinKm = 300.0, altMaxKm = 300.0) { _, _, a ->
            when (a) {
                0, 4 -> 0.1
                1, 3 -> 0.2
                else -> 0.5
            }
        }
        val grids = extractPeakGrids(volume)
        assertTrue(grids.valid)
        assertEquals(300.0f, grids.hmKm[0], 0.0f)
        assertEquals(10.25f, grids.logNe[0], 1e-5f)
        assertFalse(grids.hmKm[0].isNaN())
    }

    @Test
    fun `G8_14 extractPeakGrids layout y propagacion de dims y timestamp`() {
        val volume = testVolume(3, 2, 5, timestamp = 1723456789L) { x, y, a ->
            if (a == 1) 0.2 + 0.02 * (x + 10 * y) else 0.0
        }
        val grids = extractPeakGrids(volume)
        assertTrue(grids.valid)
        assertEquals(3, grids.width)
        assertEquals(2, grids.height)
        assertEquals(6, grids.logNe.size)
        assertEquals(6, grids.hmKm.size)
        assertEquals(1723456789L, grids.timestamp)
        // Celda (x=2, y=1) en el layout y * width + x.
        val value = 0.2f + 0.02f * (2 + 10 * 1)
        assertEquals(8.0f + 4.5f * value, grids.logNe[1 * 3 + 2], 1e-5f)
        assertEquals(8.0f + 4.5f * 0.2f, grids.logNe[0], 1e-5f)
    }

    @Test
    fun `G8_15 extractPeakGrids guardas devuelven rejillas invalidas`() {
        val short = testVolume(2, 2, 2) { _, _, _ -> 0.5 }
        val shortGrids = extractPeakGrids(short)
        assertFalse(shortGrids.valid)
        assertEquals(2, shortGrids.width)
        assertEquals(2, shortGrids.height)
        assertEquals(4, shortGrids.logNe.size)
        assertEquals(0.0f, shortGrids.logNe[0], 0.0f)
        assertEquals(0.0f, shortGrids.hmKm[0], 0.0f)

        val invalid = testVolume(2, 2, 5, valid = false) { _, _, _ -> 0.5 }
        assertFalse(extractPeakGrids(invalid).valid)
    }

    @Test
    fun `G8_16 volumeAltKmF guarda n menor o igual a 1 y exponente 1_5`() {
        assertEquals(60.0f, volumeAltKmF(0, 1, 60.0f, 500.0f), 0.0f)
        assertEquals(60.0f, volumeAltKmF(0, 20, 60.0f, 500.0f), 0.0f)
        assertEquals(500.0f, volumeAltKmF(19, 20, 60.0f, 500.0f), 1e-3f)
        // Valores fijos: con exponente 1.0 el nivel 2 de 4 daria 280, no 299.50568.
        assertEquals(299.50568f, volumeAltKmF(2, 4, 60.0f, 500.0f), 1e-3f)
        assertEquals(61.365547f, volumeAltKmF(1, 48, 60.0f, 500.0f), 1e-4f)
        assertEquals(486.03241f, volumeAltKmF(46, 48, 60.0f, 500.0f), 1e-3f)
    }
}
