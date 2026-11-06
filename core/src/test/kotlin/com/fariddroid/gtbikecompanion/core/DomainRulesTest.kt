package com.fariddroid.gtbikecompanion.core

import com.fariddroid.gtbikecompanion.core.demo.DemoScenario
import com.fariddroid.gtbikecompanion.core.demo.DemoScenarios
import com.fariddroid.gtbikecompanion.core.demo.DemoTelemetryRepository
import com.fariddroid.gtbikecompanion.core.model.AdapterLayer
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.ChannelState
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.Metric
import com.fariddroid.gtbikecompanion.core.model.Quality
import com.fariddroid.gtbikecompanion.core.model.ReceiverState
import com.fariddroid.gtbikecompanion.core.model.SensorRole
import com.fariddroid.gtbikecompanion.core.model.SensorRoleType
import com.fariddroid.gtbikecompanion.core.model.SensorSource
import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import com.fariddroid.gtbikecompanion.core.model.WorkoutGuard
import com.fariddroid.gtbikecompanion.core.rules.ActivationDecision
import com.fariddroid.gtbikecompanion.core.rules.ModActivationRule
import com.fariddroid.gtbikecompanion.core.rules.PowerZones
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricTest {
    @Test fun zeroIsAValidValue() {
        val m = Metric.valid(0.0)
        assertTrue(m.isLive)
        assertEquals(0.0, m.value!!, 0.0)
    }

    @Test fun validNeedsFiniteValue() {
        assertThrows(IllegalArgumentException::class.java) { Metric(null, Quality.VALID) }
        assertThrows(IllegalArgumentException::class.java) { Metric(Double.NaN, Quality.VALID) }
    }

    @Test fun unavailableNeedsReasonAndNoValue() {
        assertThrows(IllegalArgumentException::class.java) { Metric(null, Quality.UNAVAILABLE) }
        assertThrows(IllegalArgumentException::class.java) { Metric(0.0, Quality.UNAVAILABLE, reasonCode = "SENSOR_MISSING") }
    }

    @Test fun staleKeepsLastValueButIsNotLive() {
        val stale = Metric.valid(185.0).asStale()
        assertEquals(Quality.STALE, stale.quality)
        assertFalse(stale.isLive)
        assertEquals(185.0, stale.value!!, 0.0)
    }

    @Test fun presentSensorNeedsDeviceAndUlongStringIsAccepted() {
        assertThrows(IllegalArgumentException::class.java) { SensorRole(SensorRoleType.POWER, true, SensorSource.ANT, null, "FEC") }
        assertThrows(IllegalArgumentException::class.java) { SensorRole(SensorRoleType.POWER, true, SensorSource.ANT, "-1", "FEC") }
        SensorRole(SensorRoleType.HEART_RATE, true, SensorSource.BLE, "18446744073709551615", "HR")
    }

    @Test fun staleSnapshotRejectsLiveMetrics() {
        val live = DemoScenarios.liveCycling(0).snapshot
        assertThrows(IllegalArgumentException::class.java) {
            live.copy(layers = live.layers.copy(adapter = AdapterLayer.STALE))
        }
    }

    @Test fun unfinishedOrUnknownWorkoutBlocksSessionChanges() {
        assertTrue(WorkoutGuard(present = true, finished = false).blocksSessionChanges)
        assertTrue(WorkoutGuard(present = null, finished = null).blocksSessionChanges)
        assertFalse(WorkoutGuard(present = false, finished = null).blocksSessionChanges)
        assertFalse(WorkoutGuard(present = true, finished = true).blocksSessionChanges)
    }
}

class CapabilityTest {
    /** Mirrors policy.json forbiddenCommandPatterns: P13 adds workouts.select only (the mod's Select path). */
    @Test fun catalogHasNoErgOrWorkoutControl() {
        val forbidden = Regex("erg|erc|target|bias|^workouts?\\.(?!(read|observe|select)$)", RegexOption.IGNORE_CASE)
        CapabilityId.entries.forEach { assertFalse(it.wireId, forbidden.containsMatchIn(it.wireId)) }
        listOf("workouts.start", "workouts.pause", "workouts.skip", "workouts.end", "workouts.bias").forEach {
            assertNull(it, CapabilityId.fromWire(it))
        }
    }

    @Test fun unknownWireIdIsIgnored() {
        assertNull(CapabilityId.fromWire("erg"))
        assertEquals(CapabilityId.MOD_SET_ACTIVE, CapabilityId.fromWire("mod.setActive"))
    }
}

class ModActivationRuleTest {
    private fun bridgeFeed(ant: ReceiverState, ble: ReceiverState): TelemetryFeed {
        val demo = DemoScenarios.liveCycling(0)
        val snap = demo.snapshot.copy(layers = demo.snapshot.layers.copy(antReceiver = ant, bleReceiver = ble))
        return TelemetryFeed(
            DataOrigin.BRIDGE, ChannelState.READY, snap,
            listOf(Capability(CapabilityId.MOD_SET_ACTIVE, true, null, "mod.activate")),
        )
    }

    @Test fun demoNeverAllowsActivation() {
        val decision = ModActivationRule.decide(DemoScenarios.liveCycling(0), enabled = false)
        assertEquals(ActivationDecision.Blocked("DEMO_MODE"), decision)
    }

    @Test fun enableWhileReceiverStartingIsBlocked() {
        val feed = bridgeFeed(ReceiverState.STARTED, ReceiverState.UNINITIALIZED)
        assertEquals(ActivationDecision.Blocked("MOD_TRANSITION_IN_PROGRESS"), ModActivationRule.decide(feed, true))
    }

    @Test fun stoppingCountsAsActiveButBlocksBothDirections() {
        val feed = bridgeFeed(ReceiverState.STOPPING, ReceiverState.STOPPED)
        assertEquals(ActivationDecision.Blocked("MOD_TRANSITION_IN_PROGRESS"), ModActivationRule.decide(feed, true))
        assertEquals(ActivationDecision.Blocked("MOD_TRANSITION_IN_PROGRESS"), ModActivationRule.decide(feed, false))
    }

    @Test fun sameStateIsNoOpAndDifferentStateIsAllowed() {
        val on = bridgeFeed(ReceiverState.READY, ReceiverState.STOPPED)
        assertEquals(ActivationDecision.AlreadyInState, ModActivationRule.decide(on, true))
        assertEquals(ActivationDecision.Allowed, ModActivationRule.decide(on, false))
        val off = bridgeFeed(ReceiverState.STOPPED, ReceiverState.UNINITIALIZED)
        assertEquals(ActivationDecision.Allowed, ModActivationRule.decide(off, true))
        assertEquals(ActivationDecision.AlreadyInState, ModActivationRule.decide(off, false))
    }
}

class PowerZonesTest {
    @Test fun zonesNeedKnownFtp() {
        assertNull(PowerZones.zoneOf(200.0, null))
        assertNull(PowerZones.zoneOf(null, 250))
    }

    @Test fun boundariesAreInclusiveUpperBounds() {
        assertEquals(1, PowerZones.zoneOf(0.0, 250))
        assertEquals(1, PowerZones.zoneOf(137.5, 250))
        assertEquals(2, PowerZones.zoneOf(138.0, 250))
        assertEquals(4, PowerZones.zoneOf(250.0, 250))
        assertEquals(7, PowerZones.zoneOf(400.0, 250))
    }
}

class DemoScenariosTest {
    @Test fun everyScenarioIsMarkedDemoAndRespectsSnapshotRules() {
        DemoScenario.entries.forEach { scenario ->
            val feed = DemoScenarios.snapshotFor(scenario, 3)
            assertEquals(scenario.name, DataOrigin.DEMO, feed.origin)
            if (feed.snapshot.layers.adapter != AdapterLayer.FRESH) {
                assertTrue(scenario.name, feed.snapshot.metrics.all().none { it.isLive })
            }
        }
    }

    @Test fun repositorySwitchesScenario() = runTest {
        val repo = DemoTelemetryRepository(periodMs = 1)
        assertEquals(2, repo.feed.take(2).toList().size)
        repo.select(DemoScenario.ADAPTER_ABSENT)
        assertEquals(AdapterLayer.ABSENT, repo.feed.first().snapshot.layers.adapter)
    }
}
