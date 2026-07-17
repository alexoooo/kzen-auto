package tech.kzen.auto.client.util

import kotlinx.serialization.json.Json
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.xhr.ARRAYBUFFER
import org.w3c.xhr.EMPTY
import org.w3c.xhr.XMLHttpRequest
import org.w3c.xhr.XMLHttpRequestResponseType
import tech.kzen.auto.platform.encodeURIComponent
import kotlin.coroutines.*
import kotlin.js.Promise


// SER3: shared JSON codec for decoding REST responses into the @Serializable common DTOs. Mirrors
// kzen-launcher-js's clientJson. `ignoreUnknownKeys` so a field the server adds later doesn't break the client.
//
// Deliberately lives here rather than in ClientRestApi: that file imports kotlin.js.Json (the JS host object) as
// the declared return type of its getOrPutJson/postJson helpers, so importing kotlinx.serialization.json.Json
// there would collide. ClientRestApi already star-imports this package, so it picks clientJson up for free.
val clientJson = Json {
    ignoreUnknownKeys = true
}


class HttpStatusException(val status: Int) : RuntimeException("HTTP error: $status")


suspend fun httpGet(url: String): String = suspendCoroutine { c ->
    val xhr = XMLHttpRequest()
    xhr.onreadystatechange = {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            if (xhr.status / 100 == 2) {
                c.resume(xhr.response as String)
            }
            else {
                c.resumeWithException(HttpStatusException(xhr.status.toInt()))
            }
        }
        null
    }
    xhr.open("GET", url)
    xhr.send()
}


suspend fun httpPutForm(url: String, vararg parameters: Pair<String, String>): String = suspendCoroutine { c ->
    val xhr = XMLHttpRequest()

    xhr.onreadystatechange = {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            if (xhr.status / 100 == 2) {
                c.resume(xhr.response as String)
            }
            else {
                c.resumeWithException(HttpStatusException(xhr.status.toInt()))
            }
        }
        null
    }

    xhr.open("PUT", url)

    xhr.setRequestHeader("Content-type", "application/x-www-form-urlencoded")

    val body = parameters.joinToString("&") {
        it.first + "=" + encodeURIComponent(it.second)
    }

    xhr.send(body)
}


suspend fun httpGetBytes(url: String): ByteArray = suspendCoroutine { c ->
    val xhr = XMLHttpRequest()
    xhr.responseType = XMLHttpRequestResponseType.ARRAYBUFFER
    xhr.onreadystatechange = {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            if (xhr.status / 100 == 2) {
                val response = xhr.response as ArrayBuffer
                val decodedResponse = Uint8Array(response)
                val responseBytes = ByteArray(decodedResponse.length) { i -> decodedResponse[i] }
                c.resume(responseBytes)
            }
            else {
                c.resumeWithException(HttpStatusException(xhr.status.toInt()))
            }
        }
        null
    }
    xhr.open("GET", url)
    xhr.send()
}


suspend fun httpPostBytes(url: String, body: ByteArray): String = suspendCoroutine { c ->
    val xhr = XMLHttpRequest()
    xhr.responseType = XMLHttpRequestResponseType.EMPTY
    xhr.onreadystatechange = {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            if (xhr.status / 100 == 2) {
                c.resume(xhr.response as String)
            }
            else {
                c.resumeWithException(HttpStatusException(xhr.status.toInt()))
            }
        }
        null
    }
    xhr.open("POST", url)
    xhr.send(body)
}


suspend fun httpDelete(url: String): String = suspendCoroutine { c ->
    val xhr = XMLHttpRequest()
    xhr.onreadystatechange = {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            if (xhr.status / 100 == 2) {
                c.resume(xhr.response as String)
            }
            else {
                c.resumeWithException(HttpStatusException(xhr.status.toInt()))
            }
        }
        null
    }
    xhr.open("DELETE", url)
    xhr.send()
}


// TODO: what does this really do?
fun <T> async(x: suspend () -> T): Promise<T> {
    return Promise { resolve, reject ->
        x.startCoroutine(object: Continuation<T> {
            override fun resumeWith(result: Result<T>) {
                if (result.isSuccess) {
                    resolve(result.getOrThrow())
                }
                else {
                    reject(result.exceptionOrNull() ?: RuntimeException("Unknown failure"))
                }
            }

            override val context = EmptyCoroutineContext
        })
    }
}