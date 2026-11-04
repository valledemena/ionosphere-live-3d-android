package com.valledemena.ionosphere.data.net

/**
 * Clients HTTP directos, independientes de Android para poder doblar en tests JVM.
 * Toda la I/O de red pasa por esta interfaz; los adaptadores no la saben.
 */
interface HttpClient {
    suspend fun get(url: String, timeoutMs: Int = 25_000): HttpResult
}

data class HttpResult(val code: Int, val body: String?) {
    val ok: Boolean get() = code in 200..299
}

const val UA = "IonosphereLive3D-Android/0.x (https://github.com/valledemena/ionosphere-live-3d-android)"
