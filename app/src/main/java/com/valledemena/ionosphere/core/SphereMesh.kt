package com.valledemena.ionosphere.core

/**
 * Malla esférica UV en coordenadas de objeto, sin dependencias de Android.
 *
 * Longitud 0 en el meridiano -180 y avance hacia +180; latitud de -90 a +90.
 * La costura duplica vértices (uv.x = 0 y uv.x = 1) para no arrastrar la textura
 * a través del meridiano.
 */
class SphereMesh private constructor(
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: ShortArray,
) {

    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    companion object {
        const val DEFAULT_RINGS = 48
        const val DEFAULT_SEGMENTS = 96

        fun unitSphere(
            rings: Int = DEFAULT_RINGS,
            segments: Int = DEFAULT_SEGMENTS,
        ): SphereMesh {
            require(rings >= 2) { "rings debe ser >= 2" }
            require(segments >= 3) { "segments debe ser >= 3" }

            val vertexCount = (rings + 1) * (segments + 1)
            val positions = FloatArray(vertexCount * 3)
            val normals = FloatArray(vertexCount * 3)
            val uvs = FloatArray(vertexCount * 2)

            var v = 0
            var uvIndex = 0
            for (ring in 0..rings) {
                val phi = Math.PI * (ring.toDouble() / rings)          // 0 .. PI
                val sinPhi = Math.sin(phi)
                val cosPhi = Math.cos(phi)
                for (segment in 0..segments) {
                    val theta = 2.0 * Math.PI * (segment.toDouble() / segments) // 0 .. 2PI
                    val x = (sinPhi * Math.cos(theta)).toFloat()
                    val y = cosPhi.toFloat()
                    val z = (sinPhi * Math.sin(theta)).toFloat()

                    positions[v] = x
                    positions[v + 1] = y
                    positions[v + 2] = z

                    // Esfera unitaria: la normal es la posición.
                    normals[v] = x
                    normals[v + 1] = y
                    normals[v + 2] = z
                    v += 3

                    uvs[uvIndex++] = segment.toFloat() / segments
                    uvs[uvIndex++] = ring.toFloat() / rings
                }
            }

            val quadCount = rings * segments
            val indices = ShortArray(quadCount * 6)
            var i = 0
            for (ring in 0 until rings) {
                for (segment in 0 until segments) {
                    val top = ring * (segments + 1) + segment
                    val bottom = top + (segments + 1)
                    val topRight = top + 1
                    val bottomRight = bottom + 1

                    indices[i++] = top.toShort()
                    indices[i++] = bottom.toShort()
                    indices[i++] = topRight.toShort()

                    indices[i++] = topRight.toShort()
                    indices[i++] = bottom.toShort()
                    indices[i++] = bottomRight.toShort()
                }
            }

            return SphereMesh(positions, normals, uvs, indices)
        }
    }
}
