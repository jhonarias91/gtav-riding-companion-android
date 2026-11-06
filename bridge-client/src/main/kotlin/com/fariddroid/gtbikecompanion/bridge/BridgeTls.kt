package com.fariddroid.gtbikecompanion.bridge

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/** The PC displays its public-key fingerprint locally before the phone sends its pairing PIN. */
internal fun bridgeHttpClient(endpoint: PcEndpoint): OkHttpClient {
    val fingerprint = endpoint.keyFingerprint ?: return BridgeClient.defaultHttpClient()
    val expected = fingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val trust = object : X509TrustManager {
        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            val leaf = chain.firstOrNull() ?: throw CertificateException("Missing bridge certificate")
            leaf.checkValidity()
            val actual = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
            if (!MessageDigest.isEqual(expected, actual)) throw CertificateException("Bridge fingerprint mismatch")
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
            throw CertificateException("Client certificates are unsupported")
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
    val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return BridgeClient.defaultHttpClient().newBuilder().sslSocketFactory(ssl.socketFactory, trust).build()
}
