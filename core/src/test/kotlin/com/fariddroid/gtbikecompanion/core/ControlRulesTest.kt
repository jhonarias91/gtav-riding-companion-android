package com.fariddroid.gtbikecompanion.core

import com.fariddroid.gtbikecompanion.core.demo.DemoControlRepository
import com.fariddroid.gtbikecompanion.core.demo.DemoScenarios
import com.fariddroid.gtbikecompanion.core.model.Capability
import com.fariddroid.gtbikecompanion.core.model.CapabilityId
import com.fariddroid.gtbikecompanion.core.model.ChannelState
import com.fariddroid.gtbikecompanion.core.model.Command
import com.fariddroid.gtbikecompanion.core.model.DataOrigin
import com.fariddroid.gtbikecompanion.core.model.NumberSetting
import com.fariddroid.gtbikecompanion.core.model.Outcome
import com.fariddroid.gtbikecompanion.core.model.RouteSummary
import com.fariddroid.gtbikecompanion.core.rules.ControlRules
import com.fariddroid.gtbikecompanion.core.rules.ProfileInput
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlRulesTest {
    private val ftp = NumberSetting(230.0, 50.0, 500.0, editable = true, reasonCode = null)

    @Test fun profileValuesOutsideTheNativeRangeAreRejectedNotClamped() {
        assertEquals(ProfileInput.OutOfRange, ControlRules.parseProfileValue("501", ftp))
        assertEquals(ProfileInput.OutOfRange, ControlRules.parseProfileValue("49", ftp))
        assertEquals(ProfileInput.Invalid, ControlRules.parseProfileValue("250.5", ftp))
        assertEquals(ProfileInput.Empty, ControlRules.parseProfileValue("  ", ftp))
        assertEquals(ProfileInput.Valid(250), ControlRules.parseProfileValue(" 250 ", ftp))
    }

    @Test fun routeFlagsOnlyWhereTheCatalogAllowsThem() {
        val climb = RouteSummary("2", "Climb", null, 1.0, 1.0, 1.0, 1.0, emptyList(), loopAllowed = false, reverseAllowed = false)
        assertEquals(false to false, ControlRules.routeFlags(climb, loop = true, reverse = true))
        assertEquals(false to false, ControlRules.routeFlags(null, loop = true, reverse = true))
    }

    @Test fun reverseWithoutLoopCannotBeModelled() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteSummary("2", "x", null, null, null, null, null, emptyList(), loopAllowed = false, reverseAllowed = true)
        }
    }

    @Test fun commandsCannotCarryInvalidShapes() {
        assertThrows(IllegalArgumentException::class.java) { Command.UpdateRide(difficulty = 37, settingsRevision = "r") }
        assertThrows(IllegalArgumentException::class.java) { Command.UpdateProfile(settingsRevision = "r") }
        assertThrows(IllegalArgumentException::class.java) { Command.ApplyRoute(null, loop = true, reverse = false, catalogRevision = "c") }
    }

    @Test fun weightConversionUsesWholeKilograms() {
        assertEquals(71, ControlRules.weightToKg(157, imperial = true))
        assertEquals(157, ControlRules.kgToDisplay(71.0, imperial = true))
        assertEquals(71, ControlRules.weightToKg(71, imperial = false))
    }

    private fun bridgeFeed(vararg capabilities: Capability) = DemoScenarios.liveCycling(0)
        .copy(origin = DataOrigin.BRIDGE, channel = ChannelState.READY, capabilities = capabilities.toList())

    /** Regression (6 Nov 2026): an allowed action must not be reported as "adapter not running". */
    @Test fun anAvailableCapabilityOnAFreshPcIsNotBlocked() {
        val feed = bridgeFeed(Capability(CapabilityId.ROUTES_APPLY, true, null, "routes.apply"))
        assertNull(ControlRules.blockReason(feed, CapabilityId.ROUTES_APPLY))
    }

    @Test fun noFeedYetMeansTheAdapterIsAbsent() {
        assertEquals("ADAPTER_ABSENT", ControlRules.blockReason(null, CapabilityId.PROFILE_UPDATE))
    }

    @Test fun anUnavailableOrUnknownCapabilityShowsWhy() {
        val feed = bridgeFeed(Capability(CapabilityId.ROUTES_PREVIEW, false, "PANEL_CLOSED", "routes.pcPreview"))
        assertEquals("PANEL_CLOSED", ControlRules.blockReason(feed, CapabilityId.ROUTES_PREVIEW))
        assertEquals("CAPABILITY_UNAVAILABLE", ControlRules.blockReason(feed, CapabilityId.PROFILE_UPDATE))
    }

    @Test fun aStaleOrAbsentAdapterBlocksBeforeTheCapability() {
        val available = Capability(CapabilityId.ROUTES_APPLY, true, null, "routes.apply")
        val stale = DemoScenarios.staleAdapter().copy(origin = DataOrigin.BRIDGE, capabilities = listOf(available))
        val absent = DemoScenarios.adapterAbsent().copy(origin = DataOrigin.BRIDGE, capabilities = listOf(available))
        assertEquals("ADAPTER_STALE", ControlRules.blockReason(stale, CapabilityId.ROUTES_APPLY))
        assertEquals("ADAPTER_ABSENT", ControlRules.blockReason(absent, CapabilityId.ROUTES_APPLY))
    }

    @Test fun demoRefusesEveryCommand() = runTest {
        val demo = DemoControlRepository()
        val result = demo.submit(Command.SetModActive(true), "adapter-x")
        assertTrue(result is Outcome.Failure && result.code == "DEMO_MODE")
        val settings = (demo.settings() as Outcome.Success).value
        assertTrue(!settings.ftpW.editable && settings.ftpW.reasonCode == "DEMO_MODE")
    }
}
