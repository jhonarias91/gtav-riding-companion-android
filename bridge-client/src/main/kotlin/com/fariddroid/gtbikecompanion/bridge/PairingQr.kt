package com.fariddroid.gtbikecompanion.bridge

/**
 * The connection QR the bridge console shows (bridge/pairing-qr.mjs):
 * `gtav-riding-companion://pair?v=1&id=<bridgeId>&h=<ip>[,<ip>…]&p=<port>[&k=<sha256 hex>]&s=<pairing key>`.
 * [pairingKey] is the bridge's one-time QR key: the app pairs with it directly, so the user types nothing. Anything
 * that does not match this exact shape is refused, since the text comes from whatever the camera saw.
 */
data class PairingQr(val bridgeId: String, val hosts: List<String>, val port: Int, val keyFingerprint: String?, val pairingKey: String) {
    /** One endpoint per address, in the order the PC listed them. */
    fun endpoints(): List<PcEndpoint> = hosts.map {
        PcEndpoint(it, port, if (keyFingerprint == null) PcEndpoint.Transport.HTTP_DEV else PcEndpoint.Transport.HTTPS, keyFingerprint)
    }

    companion object {
        private const val PREFIX = "gtav-riding-companion://pair?"
        private val BRIDGE_ID = Regex("[A-Za-z0-9-]{1,64}")
        private val FINGERPRINT = Regex("[a-f0-9]{64}")
        private val PAIRING_KEY = Regex("[A-Za-z0-9_-]{22}")
        private const val MAX_HOSTS = 8

        fun parse(text: String): PairingQr? {
            if (!text.startsWith(PREFIX) || text.length > 1024) return null
            val params = text.removePrefix(PREFIX).split('&').map { it.split('=', limit = 2) }
            if (params.any { it.size != 2 }) return null
            val map = params.associate { it[0] to it[1] }
            if (map.size != params.size || map.keys.any { it !in setOf("v", "id", "h", "p", "k", "s") }) return null
            if (map["v"] != "1") return null
            val bridgeId = map["id"]?.takeIf(BRIDGE_ID::matches) ?: return null
            val port = map["p"]?.toIntOrNull()?.takeIf { it in 1024..65535 } ?: return null
            val hosts = map["h"]?.split(',')?.takeIf { it.size in 1..MAX_HOSTS } ?: return null
            if (hosts.any { !isPrivateIpv4(it) }) return null
            val fingerprint = map["k"]
            if (fingerprint != null && !FINGERPRINT.matches(fingerprint)) return null
            val pairingKey = map["s"]?.takeIf(PAIRING_KEY::matches) ?: return null
            return PairingQr(bridgeId, hosts.distinct(), port, fingerprint, pairingKey)
        }

        /** The bridge only listens on loopback and private IPv4 addresses (bridge/security.mjs privateAddress). */
        internal fun isPrivateIpv4(host: String): Boolean {
            if (!PcEndpoint.HOST.matches(host) || host == "localhost") return false
            val p = host.split('.').map(String::toInt)
            return p[0] == 127 || p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
        }
    }
}
