package it.casatv.server.server

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}

fun Response.withCors(): Response {
    addHeader("Access-Control-Allow-Origin", "*")
    addHeader("Access-Control-Allow-Headers", "content-type,range,x-profile-id,x-device-id,x-device-name,x-device-platform,x-app-version,x-playback-state,x-playback-channel,x-admin-pin")
    addHeader("Access-Control-Allow-Methods", "GET,POST,PATCH,DELETE,OPTIONS")
    addHeader("Access-Control-Expose-Headers", "content-length,content-range,accept-ranges")
    addHeader("X-Content-Type-Options", "nosniff")
    return this
}

fun jsonResponse(element: JsonElement, status: Response.IStatus = Response.Status.OK): Response {
    val bytes = element.toString().toByteArray(Charsets.UTF_8)
    val response = NanoHTTPD.newFixedLengthResponse(
        status,
        "application/json; charset=utf-8",
        java.io.ByteArrayInputStream(bytes),
        bytes.size.toLong(),
    )
    if (bytes.size < 4096) {
        response.addHeader("X-No-Gzip", "1")
    }
    return response.withCors()
}

fun errorResponse(status: Response.IStatus, message: String): Response =
    jsonResponse(
        kotlinx.serialization.json.buildJsonObject {
            put("error", JsonPrimitive(message))
        },
        status,
    )

fun textResponse(text: String, mime: String = "text/plain; charset=utf-8"): Response =
    NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, text).withCors()

fun noContent(): Response =
    NanoHTTPD.newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", "").withCors()

fun IHTTPSession.header(name: String): String =
    headers[name.lowercase()]?.trim().orEmpty()

fun IHTTPSession.parm(name: String, default: String = ""): String =
    parms[name]?.trim()?.takeIf { it.isNotEmpty() } ?: default

fun IHTTPSession.jsonBody(): JsonObject {
    val files = HashMap<String, String>()
    return try {
        val length = header("content-length").toIntOrNull() ?: 0
        val raw = if (method != Method.POST && length in 1..MAX_JSON_BYTES) {
            // NanoHTTPD only extracts bodies for POST (and may consume PATCH
            // bodies in parseBody), so read them directly here.
            val buffer = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = inputStream.read(buffer, read, length - read)
                if (count < 0) break
                read += count
            }
            String(buffer, 0, read, Charsets.UTF_8)
        } else {
            parseBody(files)
            files["postData"]
        }
        if (raw.isNullOrBlank()) JsonObject(emptyMap())
        else ApiJson.parseToJsonElement(raw).jsonObject
    } catch (_: Exception) {
        JsonObject(emptyMap())
    }
}

private const val MAX_JSON_BYTES = 1024 * 1024

/**
 * Drains a small request body on bodyless POST/PUT/PATCH endpoints so HTTP/1.1
 * keep-alive connections stay aligned for subsequent requests on the same socket.
 */
fun IHTTPSession.drainBodyIfPresent(maxBytes: Int = 64 * 1024): Boolean {
    val length = header("content-length").toIntOrNull() ?: 0
    if (length <= 0) return true
    if (length > maxBytes) return false
    return runCatching {
        val buf = ByteArray(minOf(length, 4096))
        var remaining = length
        while (remaining > 0) {
            val n = inputStream.read(buf, 0, minOf(remaining, buf.size))
            if (n < 0) break
            remaining -= n
        }
        remaining == 0
    }.getOrDefault(false)
}

/** Explicit nullable puts: avoids the String?/JsonNull union ambiguity. */
fun JsonObjectBuilder.putOrNull(key: String, value: String?) {
    put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

fun JsonObjectBuilder.putOrNull(key: String, value: Int?) {
    put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

fun JsonObjectBuilder.putOrNull(key: String, value: Long?) {
    put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

fun JsonObjectBuilder.putOrNull(key: String, value: Double?) {
    put(key, value?.let { JsonPrimitive(it) } ?: JsonNull)
}

fun JsonObject.string(key: String, default: String = ""): String =
    this[key]?.jsonPrimitive?.contentOrNullSafe() ?: default

fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull

fun JsonObject.bool(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

private fun JsonPrimitive.contentOrNullSafe(): String? =
    content.takeUnless { it == "null" }
