package com.valledemena.ionosphere.data.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

sealed class Outcome<out T> {
    data class Live<T>(val value: T, val source: String, val fetchedAtMs: Long) : Outcome<T>()
    data class Cached<T>(val value: T, val source: String, val fetchedAtMs: Long, val stale: Boolean, val reason: Throwable? = null) : Outcome<T>()
    data class Failed(val error: Throwable, val lastKnown: Any? = null) : Outcome<Nothing>()
}

data class CacheHit(val rawJson: String, val source: String, val fetchedAtMs: Long)

interface NetCache {
    suspend fun store(key: String, source: String, jsonPayload: String, fetchedAtMs: Long)
    suspend fun load(key: String): CacheHit?
}

class InMemoryCache : NetCache {
    private val map = mutableMapOf<String, CacheHit>()
    override suspend fun store(key: String, source: String, jsonPayload: String, fetchedAtMs: Long) {
        map[key] = CacheHit(jsonPayload, source, fetchedAtMs)
    }
    override suspend fun load(key: String): CacheHit? = map[key]
}

class FileNetCache(private val directory: File) : NetCache {
    private fun file(key: String): File = File(directory, "$key.json")
    override suspend fun store(key: String, source: String, jsonPayload: String, fetchedAtMs: Long) {
        val f = file(key)
        f.parentFile?.mkdirs()
        val envelope = NetEnvelope(fetchedAt = fetchedAtMs, source = source, payload = Json.parseToJsonElement(jsonPayload))
        f.writeText(Json.encodeToString(NetEnvelope.serializer(), envelope))
    }
    override suspend fun load(key: String): CacheHit? {
        val f = file(key)
        if (!f.exists()) return null
        val envelope = runCatching { Json.decodeFromString<com.valledemena.ionosphere.data.net.NetEnvelope>(f.readText()) }.getOrNull() ?: return null
        return CacheHit(envelope.payload.toString(), envelope.source, envelope.fetchedAt)
    }
}

@kotlinx.serialization.Serializable
private data class NetEnvelope(
    val fetchedAt: Long,
    val source: String,
    val payload: kotlinx.serialization.json.JsonElement,
)