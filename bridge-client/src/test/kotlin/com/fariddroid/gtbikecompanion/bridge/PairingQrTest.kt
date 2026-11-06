package com.fariddroid.gtbikecompanion.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same format as bridge/pairing-qr.mjs (tests/auto-tls.test.mjs checks the bridge side). */
class PairingQrTest {
    private val fp = "a".repeat(64)
    private val key = "Qx_-".repeat(5) + "zz"

    @Test fun parsesWhatTheBridgePrints() {
        val qr = PairingQr.parse("gtav-riding-companion://pair?v=1&id=bridge-c016498b8324&h=192.168.1.27,10.0.0.5&p=8788&k=$fp&s=$key")!!
        assertEquals(PairingQr("bridge-c016498b8324", listOf("192.168.1.27", "10.0.0.5"), 8788, fp, key), qr)
        assertEquals(
            listOf(PcEndpoint("192.168.1.27", 8788, PcEndpoint.Transport.HTTPS, fp), PcEndpoint("10.0.0.5", 8788, PcEndpoint.Transport.HTTPS, fp)),
            qr.endpoints(),
        )
    }

    @Test fun withoutFingerprintItIsDevelopmentHttp() {
        val qr = PairingQr.parse("gtav-riding-companion://pair?v=1&id=bridge-1&h=127.0.0.1&p=8799&s=$key")!!
        assertEquals(listOf(PcEndpoint("127.0.0.1", 8799, PcEndpoint.Transport.HTTP_DEV, null)), qr.endpoints())
    }

    @Test fun refusesAnythingElse() {
        val good = "gtav-riding-companion://pair?v=1&id=bridge-1&h=192.168.1.27&p=8788&k=$fp&s=$key"
        listOf(
            "https://example.com/?v=1&id=bridge-1&h=192.168.1.27&p=8788",
            good.replace("v=1", "v=2"),
            good.replace("h=192.168.1.27", "h=8.8.8.8"), // public address: never the bridge
            good.replace("h=192.168.1.27", "h=pc.local"),
            good.replace("h=192.168.1.27", "h=localhost"),
            good.replace("p=8788", "p=80"),
            good.replace("k=$fp", "k=${"A".repeat(64)}"),
            good.replace("id=bridge-1", "id=bridge 1"),
            "$good&pin=123456", // unknown field
            "$good&p=9000", // repeated field
            good.replace("&p=8788", "&p"),
            good.replace("&s=$key", ""), // no pairing key
            good.replace("s=$key", "s=123456"),
        ).forEach { assertNull(it, PairingQr.parse(it)) }
    }
}
