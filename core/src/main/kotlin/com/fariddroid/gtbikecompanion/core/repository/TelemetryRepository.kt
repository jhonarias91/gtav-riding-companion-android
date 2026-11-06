package com.fariddroid.gtbikecompanion.core.repository

import com.fariddroid.gtbikecompanion.core.model.TelemetryFeed
import kotlinx.coroutines.flow.Flow

/** Single shared feed per PC (PLAN_ANDROID.md §6): screens never poll on their own. */
interface TelemetryRepository {
    val feed: Flow<TelemetryFeed>
}
