package com.fariddroid.gtbikecompanion.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fariddroid.gtbikecompanion.data.AppPreferences
import com.fariddroid.gtbikecompanion.data.AppSettings
import com.fariddroid.gtbikecompanion.data.UnitSystem
import com.fariddroid.gtbikecompanion.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(private val preferences: AppPreferences) : ViewModel() {
    /** null until DataStore has been read once, so the start destination is never guessed. */
    val settings: StateFlow<AppSettings?> = preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setOnboardingDone(done: Boolean) = viewModelScope.launch { preferences.setOnboardingDone(done) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { preferences.setTheme(mode) }
    fun setUnits(units: UnitSystem) = viewModelScope.launch { preferences.setUnits(units) }
}
