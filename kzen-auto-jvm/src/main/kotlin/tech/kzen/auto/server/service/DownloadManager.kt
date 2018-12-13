package tech.kzen.auto.server.service

import com.google.common.io.ByteStreams
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


class DownloadManager {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val logger = LoggerFactory.getLogger(DownloadManager::class.java)!!
    }


    //-----------------------------------------------------------------------------------------------------------------
    // TODO: implement proper certificate management
    fun initialize() {
        // https://stackoverflow.com/a/24501156

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? = null
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        })

        val sc = SSLContext.getInstance("SSL")
        sc.init(null, trustAllCerts, java.security.SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.socketFactory)

        val allHostsValid = HostnameVerifier { _, _ -> true }

        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun download(location: URI): ByteArray {
        logger.info("downloading: {}", location)

        val bytes = location
                .toURL()
                .openStream()
                .use { ByteStreams.toByteArray(it) }

        logger.info("download complete: {}", bytes.size)

        return bytes
    }
}