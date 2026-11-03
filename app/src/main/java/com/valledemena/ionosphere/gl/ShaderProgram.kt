package com.valledemena.ionosphere.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log

/**
 * Compila el par de shaders eligiendo la cabecera GLSL disponible en el contexto:
 * `#version 310 es` si el driver la acepta, `#version 300 es` como respaldo.
 *
 * El cuerpo es idéntico: R1 no usa compute ni SSBO, así que las dos versiones
 * producen el mismo resultado. Se prefiere 310 es porque es la versión que fija
 * `ARCHITECTURE_DECISION.md` §1 para el renderer propio.
 */
internal object ShaderProgram {

    private const val TAG = "GlobeShader"
    private val HEADERS = listOf("#version 310 es\n", "#version 300 es\n")

    fun create(vertexBody: String, fragmentBody: String): Int {
        var lastError = ""
        for (header in HEADERS) {
            val vertex = compile(GLES20.GL_VERTEX_SHADER, header + vertexBody)
            if (vertex == 0) {
                lastError = "vertex con $header"
                continue
            }
            val fragment = compile(GLES20.GL_FRAGMENT_SHADER, header + fragmentBody)
            if (fragment == 0) {
                GLES20.glDeleteShader(vertex)
                lastError = "fragment con $header"
                continue
            }
            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)

            val status = IntArray(1)
            GLES30.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == GLES20.GL_TRUE) {
                Log.i(TAG, "Programa enlazado con ${header.trim()}")
                return program
            }
            lastError = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
        }
        throw IllegalStateException("No se pudo crear el programa GLSL: $lastError")
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == GLES20.GL_TRUE) return shader
        Log.e(TAG, "Error de compilación: ${GLES20.glGetShaderInfoLog(shader)}")
        GLES20.glDeleteShader(shader)
        return 0
    }
}
