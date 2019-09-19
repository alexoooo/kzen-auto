package tech.kzen.auto.client.util

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.xhr.ARRAYBUFFER
import org.w3c.xhr.XMLHttpRequest
import org.w3c.xhr.XMLHttpRequestResponseType
import kotlin.coroutines.*
import kotlin.js.Promise


external fun encodeURIComponent(str: String): String


suspend fun httpGet(url: String): String = suspendCoroutine { c ->
//    console.log("^^^ httpGet", url)

    val xhr = XMLHttpRequest()
    xhr.onreadystatechange = {
        if (xhr.readyState == XMLHttpRequest.DONE) {
            if (xhr.status / 100 == 2) {
//                console.log("^^^ httpGet - xhr.response", xhr.response)
                c.resume(xhr.response as String)
            }
            else {
                c.resumeWithException(RuntimeException("HTTP error: ${xhr.status}"))
            }
        }
        null
    }
    xhr.open("GET", url)
    xhr.send()
}


suspend fun httpGetBytes(url: String): ByteArray = suspendCoroutine { c ->
//    console.log("^^^ httpGet", url)

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
                c.resumeWithException(RuntimeException("HTTP error: ${xhr.status}"))
            }
        }
        null
    }
    xhr.open("GET", url)
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
                    reject(result.exceptionOrNull()!!)
                }
            }
//
            override val context = EmptyCoroutineContext
        })
    }
}