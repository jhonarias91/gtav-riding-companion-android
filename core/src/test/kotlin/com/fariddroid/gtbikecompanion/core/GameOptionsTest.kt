package com.fariddroid.gtbikecompanion.core

import com.fariddroid.gtbikecompanion.core.model.BotType
import com.fariddroid.gtbikecompanion.core.model.GameOptionRules
import com.fariddroid.gtbikecompanion.core.model.GameOptions
import com.fariddroid.gtbikecompanion.core.model.TrafficStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameOptionsTest {
    private val file = GameOptions(
        trafficBikesPercent = 50, trafficStyle = TrafficStyle.NORMAL, crowdsEnabled = true, crowdsSlopePercent = 6, crowdsAmount = 50,
        bots = listOf(BotType.CLIMBER, BotType.TIME_TRIALER), defensiveLevel = 1, waypointColor = "FFADFF2F", waypointRadius = 20,
        fineSteering = 0.7, screenshotIntervalKm = 5, screenshotXOffset = 0, menuAutoOpen = true, showClock = true, debugWindow = false,
        logLevel = 3, gpsOriginLat = -19.10637, gpsOriginLon = -169.871, telemetry = true, multiplayer = true,
    )

    @Test fun onlyChangedFieldsAreSentWithWireValues() {
        val edited = file.copy(trafficStyle = TrafficStyle.AGGRESSIVE, bots = listOf(BotType.COMPANION), multiplayer = false, fineSteering = 0.70000001)
        assertEquals(mapOf("trafficStyle" to "AGRESSIVE", "bots" to listOf(1), "multiplayer" to false), GameOptionRules.changes(file, edited))
        assertTrue(GameOptionRules.changes(file, file).isEmpty())
    }

    @Test fun missingLinesInTheFileAreWrittenOnSave() {
        val current = file.copy(logLevel = null)
        assertEquals(mapOf("logLevel" to 3), GameOptionRules.changes(current, current.orDefaults(file)))
    }

    @Test fun noBotsIsAnEmptyListNotAMissingValue() {
        assertEquals(mapOf("bots" to emptyList<Int>()), GameOptionRules.changes(file, file.copy(bots = emptyList())))
    }

    @Test fun rangesMatchTheBridge() {
        assertTrue(GameOptionRules.invalid(file).isEmpty())
        val bad = file.copy(logLevel = 6, waypointRadius = 41, waypointColor = "red", bots = List(6) { BotType.RANDOM }, gpsOriginLat = 91.0, fineSteering = 0.05)
        assertEquals(listOf("bots", "waypointColor", "waypointRadius", "fineSteering", "logLevel", "gpsOriginLat"), GameOptionRules.invalid(bad))
    }
}
