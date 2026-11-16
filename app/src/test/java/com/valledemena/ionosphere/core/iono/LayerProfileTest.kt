package com.valledemena.ionosphere.core.iono

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class LayerProfileTest {

    private fun dayNoF1(): LayerProfile = LayerProfile(
        nmE = 1.116e11,
        hmE = 110.0,
        nmF1 = 0.0,
        hmF1 = 180.0,
        nmF2 = 1.24e12,
        hmF2 = 300.0,
        b0 = 100.0,
        b1 = 3.0,
        hasE = true,
        hasF1 = false,
        hasF2 = true,
    )

    private fun nightF2(): LayerProfile = blankProfile().copy(
        nmF2 = 3e11,
        hmF2 = 350.0,
        b0 = 120.0,
        b1 = 3.0,
        hasF2 = true,
    )

    private fun evalRefMax(
        p: LayerProfile,
        hKm: Double,
        cosChi: Double,
    ): NeWinner {
        var best = 0.0
        var win = -1
        if (cosChi > 0.0) {
            val v = chapmanAlpha(1e9 * kotlin.math.sqrt(cosChi), 75.0, 10.0, hKm)
            if (v > best) {
                best = v
                win = 0
            }
        }
        if (p.hasE) {
            val v = chapmanAlpha(p.nmE, p.hmE, 10.0, hKm)
            if (v > best) {
                best = v
                win = 1
            }
        }
        if (p.hasF1) {
            val v = chapmanAlpha(p.nmF1, p.hmF1, 25.0, hKm)
            if (v > best) {
                best = v
                win = 2
            }
        }
        if (p.hasF2) {
            var neF2: Double
            if (hKm <= p.hmF2) {
                val b0 = if (p.b0 > 0.0) p.b0 else 100.0
                val x = (p.hmF2 - hKm) / b0
                val xc = if (x < 1.6) x else 1.6
                val base = p.nmF2 * kotlin.math.exp(
                    -xc.pow(p.b1) / kotlin.math.cosh(xc),
                )
                neF2 = if (x > 1.6) {
                    base * kotlin.math.exp(-(x - 1.6) / 1.2)
                } else {
                    base
                }
            } else {
                val hTop = 60.0 * (1.0 + (hKm - p.hmF2) / 300.0)
                neF2 = p.nmF2 * kotlin.math.exp(-(hKm - p.hmF2) / hTop)
            }
            if (neF2 > best) {
                best = neF2
                win = 3
            }
        }
        return NeWinner(best, win)
    }

    @Test
    fun `G7_7 chapmanAlpha pico y saneado oraculo-exacto`() {
        assertEquals(1.5e11, chapmanAlpha(1.5e11, 110.0, 10.0, 110.0), 0.0)

        assertEquals(100.0, sanitizeB0(0.0), 0.0)
        assertEquals(20.0, sanitizeB0(10.0), 0.0)
        assertEquals(300.0, sanitizeB0(400.0), 0.0)
        assertEquals(100.0, sanitizeB0(Double.NaN), 0.0)
        assertEquals(100.0, sanitizeB0(Double.NEGATIVE_INFINITY), 0.0)
        assertEquals(300.0, sanitizeB0(Double.POSITIVE_INFINITY), 0.0)

        assertEquals(3.0, sanitizeB1(0.0), 0.0)
        assertEquals(1.7, sanitizeB1(1.0), 0.0)
        assertEquals(4.0, sanitizeB1(9.0), 0.0)
        assertEquals(3.0, sanitizeB1(Double.NaN), 0.0)
        assertEquals(4.0, sanitizeB1(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun `G7_8 profileFromFo mapea fo-Nm con flags y hmF1`() {
        assertEquals(false, blankProfile().hasE)

        val p = profileFromFo(4.0, 3.0, 7.0, 300.0, 0.0, 1.0)
        assertTrue(p.hasE && p.hasF1 && p.hasF2)
        assertEquals(1.24e10 * 16.0, p.nmE, 0.0)
        assertEquals(1.24e10 * 49.0, p.nmF2, 0.0)
        assertEquals(240.0, p.hmF1, 0.0)
        assertEquals(100.0, p.b0, 0.0)
        assertEquals(1.7, p.b1, 0.0)

        val q = profileFromFo(4.0, 0.0, 7.0, 300.0, 0.0, 1.0)
        assertEquals(false, q.hasF1)
        assertEquals(180.0, q.hmF1, 0.0)
    }

    @Test
    fun `G7_9 f2FloorKm pinea suelos exactos del oraculo`() {
        assertEquals(110.0, f2FloorKm(dayNoF1()), 0.0)
        assertEquals(-1.0, f2FloorKm(nightF2()), 0.0)

        val strongE = blankProfile().copy(
            nmE = 1.8e12,
            hmE = 110.0,
            nmF2 = 1e12,
            hmF2 = 300.0,
            b0 = 60.0,
            b1 = 2.5,
            hasE = true,
            hasF2 = true,
        )
        assertEquals(167.0, f2FloorKm(strongE), 0.0)

        val realShape = listOf(
            Triple(60.0, 145.0, 1.24e10 * 16.0),
            Triple(45.0, 155.0, 1.24e10 * 16.0),
            Triple(80.0, 136.0, 1.24e10 * 16.0),
        )
        for ((b0, want, nmE) in realShape) {
            val p = blankProfile().copy(
                nmE = nmE,
                hmE = 110.0,
                nmF2 = 1.24e10 * 49.0,
                hmF2 = 300.0,
                b0 = b0,
                b1 = 3.0,
                hasE = true,
                hasF2 = true,
            )
            assertEquals(want, f2FloorKm(p), 0.0)
        }
    }

    @Test
    fun `G7_10 continuidad del suelo B0 mas-menos 2`() {
        fun mk(b0: Double): LayerProfile = blankProfile().copy(
            nmE = 1.8e12,
            hmE = 110.0,
            nmF2 = 1e12,
            hmF2 = 300.0,
            b0 = b0,
            b1 = 2.5,
            hasE = true,
            hasF2 = true,
        )
        val fa = f2FloorKm(mk(58.0))
        val fb = f2FloorKm(mk(62.0))
        assertTrue(fa > 110.0 && fb > 110.0 && kotlin.math.abs(fa - fb) <= 15.0)
    }

    @Test
    fun `G7_11 winners por margenes y empates`() {
        val p = dayNoF1()
        assertEquals(0, evalNeTotal(p, 75.0, 0.9, 1.0).winner)
        assertEquals(1, evalNeTotal(p, 110.0, 0.9, 1.0).winner)
        assertEquals(3, evalNeTotal(p, 200.0, 0.9, 1.0).winner)
        assertEquals(3, evalNeTotal(p, 300.0, 0.9, 1.0).winner)

        val withF1 = p.copy(nmF1 = 3.1e11, hmF1 = 240.0, hasF1 = true)
        assertEquals(110.0, f2FloorKm(withF1), 0.0)
        assertEquals(0, evalNeTotal(withF1, 60.0, 0.9, 1.0).winner)
        assertEquals(1, evalNeTotal(withF1, 100.0, 0.9, 1.0).winner)

        val n = nightF2()
        assertEquals(3, evalNeTotal(n, 60.0, -0.5, 1.0).winner)
        assertEquals(3, evalNeTotal(n, 300.0, -0.5, 1.0).winner)

        val d = p.copy(hasE = false)
        assertEquals(-1.0, f2FloorKm(d), 0.0)
        assertEquals(3, evalNeTotal(d, 60.0, 0.9, 1.0).winner)

        assertEquals(NeWinner(0.0, -1), evalNeTotal(blankProfile(), 200.0, -0.5, 1.0))
    }

    @Test
    fun `G7_11b invariante best contra max sin puerta`() {
        val ps = listOf(
            dayNoF1(),
            dayNoF1().copy(nmF1 = 3.1e11, hmF1 = 240.0, hasF1 = true),
            nightF2(),
            dayNoF1().copy(hasE = false),
        )
        val hs = listOf(60.0, 80.0, 100.0, 120.0, 150.0, 180.0, 200.0,
            250.0, 300.0, 400.0, 500.0, 700.0)
        for ((pi, p) in ps.withIndex()) {
            for (h in hs) {
                val cosChi = if (pi == 2) -0.5 else 0.9
                val a = evalNeTotal(p, h, cosChi, 1.0)
                val b = evalRefMax(p, h, cosChi)
                assertEquals(a.ne, b.ne, 0.0)
                if (b.winner == 3 && a.winner != 3) {
                    assertTrue(h <= f2FloorKm(p))
                } else {
                    assertEquals(a.winner, b.winner)
                }
            }
        }
    }

    @Test
    fun `G7_12 normLogNe normaliza y satura`() {
        assertEquals(0.0, normLogNe(1e8), 1e-12)
        assertEquals(1.0, normLogNe(1e13), 0.0)
        assertEquals(0.5, normLogNe(10.0.pow(10.25)), 1e-12)
        assertEquals(0.0, normLogNe(0.0), 0.0)
        assertEquals(0.0, normLogNe(Double.NaN), 0.0)
        assertEquals(0.0, normLogNe(Double.NEGATIVE_INFINITY), 0.0)
        assertEquals(1.0, normLogNe(Double.POSITIVE_INFINITY), 0.0)
    }

    @Test
    fun `G7_12b builder invalido devuelve arrays completos a cero`() {
        val v = buildDensityVolume(
            profiles = listOf(dayNoF1()),
            sunDir = doubleArrayOf(-1.0, 0.0, 0.0),
            lonRes = 2,
            latRes = 2,
            altRes = 3,
            altMinKm = 60.0,
            altMaxKm = 500.0,
        )
        assertEquals(false, v.valid)
        assertEquals(12, v.data.size)
        assertEquals(12, v.layer.size)
        assertEquals(0L, v.timestamp)
        assertTrue(v.data.all { it == 0.0 })
        assertTrue(v.layer.all { it == 0 })
    }

    @Test
    fun `G7_12c builder 3x2x4 valido y en rangos`() {
        val profiles = List(6) { dayNoF1() }
        val v = buildDensityVolume(
            profiles = profiles,
            sunDir = doubleArrayOf(-1.0, 0.0, 0.0),
            lonRes = 3,
            latRes = 2,
            altRes = 4,
            altMinKm = 60.0,
            altMaxKm = 500.0,
        )
        assertTrue(v.valid)
        assertEquals(24, v.data.size)
        assertEquals(24, v.layer.size)
        assertTrue(v.data.all { it in 0.0..1.0 })
        assertTrue(v.layer.all { it in 0..3 })
    }

    @Test
    fun `G7_12d builder coincide con evaluacion celda a celda`() {
        val lonRes = 3
        val latRes = 2
        val altRes = 4
        val profiles = List(lonRes * latRes) { dayNoF1() }
        val sunDir = doubleArrayOf(-1.0, 0.0, 0.0)
        val v = buildDensityVolume(
            profiles = profiles,
            sunDir = sunDir,
            lonRes = lonRes,
            latRes = latRes,
            altRes = altRes,
            altMinKm = 60.0,
            altMaxKm = 500.0,
        )
        for (latIdx in 0 until latRes) {
            val latDeg = -90.0 + (180.0 * (latIdx + 0.5)) / latRes
            for (lonIdx in 0 until lonRes) {
                val lonDeg = -180.0 + (360.0 * (lonIdx + 0.5)) / lonRes
                val dir = densityDir(latDeg, lonDeg)
                val cosChi = dir[0] * sunDir[0] +
                    dir[1] * sunDir[1] +
                    dir[2] * sunDir[2]
                val p = profiles[latIdx * lonRes + lonIdx]
                for (a in 0 until altRes) {
                    val h = volumeAltKm(a, altRes, 60.0, 500.0)
                    val r = evalNeTotal(p, h, cosChi, 1.0)
                    val ii = volumeIndex(lonIdx, latIdx, a, lonRes, latRes)
                    assertEquals(normLogNe(r.ne), v.data[ii], 0.0)
                    val wantLayer = when {
                        r.winner < 0 -> 0
                        r.winner > 3 -> 3
                        else -> r.winner
                    }
                    assertEquals(wantLayer, v.layer[ii])
                }
            }
        }
    }

    @Test
    fun `G7_12e builder 72x72x48 completo y ultimo indice`() {
        val v = buildDensityVolume(
            profiles = List(72 * 72) { dayNoF1() },
            sunDir = doubleArrayOf(-1.0, 0.0, 0.0),
        )
        assertTrue(v.valid)
        assertEquals(248832, v.data.size)
        assertEquals(248832, v.layer.size)
        assertEquals(248831, volumeIndex(71, 71, 47, 72, 72))
        assertTrue(v.data.all { it in 0.0..1.0 })
        assertTrue(v.layer.all { it in 0..3 })
    }
}
