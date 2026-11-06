package com.fariddroid.gtbikecompanion.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fariddroid.gtbikecompanion.ui.theme.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class UnitSystem { METRIC, IMPERIAL }

data class AppSettings(val theme: ThemeMode, val units: UnitSystem, val onboardingDone: Boolean)

/**
 * App-only preferences (plan §6). Locale is NOT stored here: it lives in AppCompat / system per-app
 * language so there is a single source of truth. No secrets in DataStore (tokens go to Keystore in P2).
 */
@Singleton
class AppPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                theme = p[THEME].toEnum(ThemeMode.SYSTEM),
                units = p[UNITS].toEnum(UnitSystem.METRIC),
                onboardingDone = p[ONBOARDING_DONE] ?: false,
            )
        }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }
    suspend fun setUnits(units: UnitSystem) = store.edit { it[UNITS] = units.name }
    suspend fun setOnboardingDone(done: Boolean) = store.edit { it[ONBOARDING_DONE] = done }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val UNITS = stringPreferencesKey("units")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

        inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
            this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
    }
}
