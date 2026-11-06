package com.fariddroid.gtbikecompanion.bridge

import com.fariddroid.gtbikecompanion.core.model.Outcome
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in check against a real bridge with its automatic certificate: pinned key, address check and hello.
 * Start the bridge (node bridge/start-v1.mjs --host 127.0.0.1 --port 8799), then run the tests with
 * GTBIKE_LIVE_BRIDGE=127.0.0.1:8799:<bridge key fingerprint>. Skipped otherwise.
 */
class LiveBridgeTlsTest {
    private val live = System.getenv("GTBIKE_LIVE_BRIDGE")?.split(':')?.takeIf { it.size == 3 }

    @Test fun pinnedHttpsHelloAgainstTheRealBridge() = runBlocking {
        assumeTrue("GTBIKE_LIVE_BRIDGE not set", live != null)
        val (host, port, fingerprint) = live!!
        val hello = BridgeClient(PcEndpoint(host, port.toInt(), PcEndpoint.Transport.HTTPS, fingerprint), token = { null }).hello()
        assertTrue("hello failed: $hello", hello is Outcome.Success)
        assertEquals("https", (hello as Outcome.Success).value.transport)
    }

    /** GTBIKE_LIVE_QR = the exact text of the console QR. Pairs over HTTPS with its one-time key, then the key is used up. */
    @Test fun scannedQrPairsWithoutTypingAnything() = runBlocking {
        val text = System.getenv("GTBIKE_LIVE_QR")
        assumeTrue("GTBIKE_LIVE_QR not set", text != null)
        val qr = PairingQr.parse(text!!)
        assertTrue("the bridge's QR must parse", qr != null)
        val client = BridgeClient(qr!!.endpoints().first(), token = { null })
        assertEquals(qr.bridgeId, (client.hello() as Outcome.Success).value.bridgeId)
        assertTrue(client.pairWithKey(qr.pairingKey, "Live test") is Outcome.Success)
        assertEquals("PAIRING_PIN_INVALID", (client.pairWithKey(qr.pairingKey, "Live test") as Outcome.Failure).code)
    }

    @Test fun anotherFingerprintIsRefused() = runBlocking {
        assumeTrue("GTBIKE_LIVE_BRIDGE not set", live != null)
        val (host, port, fingerprint) = live!!
        val wrong = fingerprint.map { if (it == '0') '1' else '0' }.joinToString("")
        val hello = BridgeClient(PcEndpoint(host, port.toInt(), PcEndpoint.Transport.HTTPS, wrong), token = { null }).hello()
        assertTrue("a different key must be refused: $hello", hello is Outcome.Failure)
    }
}
