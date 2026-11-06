package com.fariddroid.gtbikecompanion.core.rules

/**
 * Seven-zone model by % of FTP (common Coggan-style bands). Zones need a known FTP:
 * without it the UI shows watts only, never zones computed from a guessed value.
 */
object PowerZones {
    /** Upper bound (inclusive) of zones 1..6 as a fraction of FTP; zone 7 is everything above. */
    private val upperBounds = doubleArrayOf(0.55, 0.75, 0.90, 1.05, 1.20, 1.50)

    const val COUNT = 7

    /** 1-based zone for [watts], or null when power or FTP is unknown. */
    fun zoneOf(watts: Double?, ftpW: Int?): Int? {
        if (watts == null || ftpW == null || ftpW <= 0 || watts < 0) return null
        val ratio = watts / ftpW
        val index = upperBounds.indexOfFirst { ratio <= it }
        return if (index == -1) COUNT else index + 1
    }

    /** Lower/upper fractions of FTP for gauge drawing. Zone 7 is open-ended; callers cap it. */
    fun bounds(zone: Int): Pair<Double, Double?> {
        require(zone in 1..COUNT)
        val lower = if (zone == 1) 0.0 else upperBounds[zone - 2]
        val upper = if (zone == COUNT) null else upperBounds[zone - 1]
        return lower to upper
    }
}
