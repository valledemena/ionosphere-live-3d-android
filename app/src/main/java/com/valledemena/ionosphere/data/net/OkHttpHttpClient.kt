package com.valledemena.ionosphere.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Implementacion real de [HttpClient] sobre OkHttp. Lleva el User-Agent de §5
 * del DATA_CONTRACT y los timeouts de 12-25 s (aqui 25, maximo del contrato).
 */
class OkHttpHttpClient(
    private val timeoutMs: Long = 25_000L,
) : HttpClient {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            val decorated = chain.request().newBuilder()
                .header("User-Agent", UA)
                .build()
            chain.proceed(decorated)
        }
        .build()

    override suspend fun get(url: String, timeoutMs: Int): HttpResult =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                HttpResult(code = resp.code, body = resp.body?.string())
            }
        }
}
