package com.fariddroid.gtbikecompanion.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.fariddroid.gtbikecompanion.core.demo.DemoControlRepository
import com.fariddroid.gtbikecompanion.core.demo.DemoTelemetryRepository
import com.fariddroid.gtbikecompanion.core.repository.ControlRepository
import com.fariddroid.gtbikecompanion.core.repository.TelemetryRepository
import com.fariddroid.gtbikecompanion.data.ActiveControlRepository
import com.fariddroid.gtbikecompanion.data.ActiveTelemetryRepository
import com.fariddroid.gtbikecompanion.data.ApplicationScope
import com.fariddroid.gtbikecompanion.data.PairingStoreQualifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * P2: screens read the Active* repositories, which follow ConnectionManager.mode (demo or paired PC).
 * The demo repositories stay injectable for the debug scenario switcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun demoTelemetryRepository(): DemoTelemetryRepository = DemoTelemetryRepository()

    @Provides
    @Singleton
    fun demoControlRepository(): DemoControlRepository = DemoControlRepository()

    @Provides
    @Singleton
    fun telemetryRepository(active: ActiveTelemetryRepository): TelemetryRepository = active

    @Provides
    @Singleton
    fun controlRepository(active: ActiveControlRepository): ControlRepository = active

    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    /** Separate file so backup rules can exclude exactly the pairing. */
    @Provides
    @Singleton
    @PairingStoreQualifier
    fun pairingDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("pairing") }
}
