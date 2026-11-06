package com.fariddroid.gtbikecompanion

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.fariddroid.gtbikecompanion.data.KeystoreTokenVault
import com.fariddroid.gtbikecompanion.data.PairingStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingStoreIdTest {
    @Test fun clientIdSurvivesForgetAndStoreReload() = runBlocking {
        val directory = Files.createTempDirectory("pairing-id-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "pairing.preferences_pb") }
            val pairing = PairingStore(dataStore, KeystoreTokenVault())
            val id = pairing.clientId()
            assertTrue(id.matches(Regex("[a-f0-9]{32}")))
            pairing.clear()
            assertEquals(id, PairingStore(dataStore, KeystoreTokenVault()).clientId())
        } finally {
            scope.cancel()
            directory.deleteRecursively()
        }
    }
}
