package com.fariddroid.gtbikecompanion

import com.fariddroid.gtbikecompanion.bridge.PcEndpoint
import com.fariddroid.gtbikecompanion.data.PairedPc
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bridge accepts a token for 30 days; Ride offers the PIN-only renewal once that date has passed. */
class PairedPcTest {
    private val endpoint = PcEndpoint("192.168.1.50", 8788, PcEndpoint.Transport.HTTP_DEV, null)
    private val pairedAt = Instant.parse("2026-11-05T10:00:00Z")
    private val pc = PairedPc("bridge-1", endpoint, "token", pairedAt.plus(Duration.ofDays(30)))

    @Test fun validUntilTheExpiryInstant() {
        assertFalse(pc.isExpired(pairedAt.plus(Duration.ofDays(29))))
        assertTrue(pc.isExpired(pairedAt.plus(Duration.ofDays(30))))
        assertTrue(pc.isExpired(pairedAt.plus(Duration.ofDays(31))))
    }

    @Test fun pairingsSavedBeforeTheDateWasKeptAreNeverReportedAsExpired() {
        assertFalse(pc.copy(expiresAt = null).isExpired(pairedAt.plus(Duration.ofDays(365))))
    }
}
