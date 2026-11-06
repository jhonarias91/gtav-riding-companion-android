package com.fariddroid.gtbikecompanion.data

import com.fariddroid.gtbikecompanion.BuildConfig

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fariddroid.gtbikecompanion.bridge.PcEndpoint
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PairingStoreQualifier

/**
 * A PC this phone paired with. The address can change; [bridgeId] is the identity the user verified.
 * [expiresAt] is when the PC stops accepting [token] (30 days after pairing); null for pairings saved before it was kept.
 */
data class PairedPc(val bridgeId: String, val endpoint: PcEndpoint, val token: String, val expiresAt: Instant? = null) {
    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt != null && !now.isBefore(expiresAt)
}

/**
 * AES-GCM with a non-exportable Android Keystore key (plan §15). Only ciphertext reaches DataStore. If the key
 * is gone (app data cleared, restore on another device), decryption fails and the phone simply pairs again.
 */
@Singleton
class KeystoreTokenVault @Inject constructor() {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): Pair<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) to Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    fun decrypt(iv: String, ciphertext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "gtbike_bridge_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

/** Persists one paired PC in its own DataStore file, excluded from backup (backup_rules.xml and data_extraction_rules.xml). */
@Singleton
class PairingStore @Inject constructor(
    @PairingStoreQualifier private val store: DataStore<Preferences>,
    private val vault: KeystoreTokenVault,
) {
    /** Random per-installation ID, kept when the user forgets a PC and excluded from Android backup. */
    suspend fun clientId(): String = store.edit { preferences ->
        if (preferences[CLIENT_ID] == null) preferences[CLIENT_ID] = UUID.randomUUID().toString().replace("-", "")
    }[CLIENT_ID]!!

    suspend fun load(): PairedPc? {
        val p = store.data.first()
        val host = p[HOST] ?: return null
        val port = p[PORT] ?: return null
        val bridgeId = p[BRIDGE_ID] ?: return null
        val transport = PcEndpoint.Transport.entries.firstOrNull { it.name == p[TRANSPORT] } ?: return null
        val token = vault.decrypt(p[IV] ?: return null, p[TOKEN] ?: return null) ?: return null
        if (transport == PcEndpoint.Transport.HTTP_DEV && !BuildConfig.DEBUG) return null
        val endpoint = runCatching { PcEndpoint(host, port, transport, p[KEY_FINGERPRINT]) }.getOrNull() ?: return null
        val expiresAt = p[EXPIRES_AT]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return PairedPc(bridgeId, endpoint, token, expiresAt)
    }

    suspend fun save(pc: PairedPc) {
        val (iv, ciphertext) = vault.encrypt(pc.token)
        store.edit {
            it[HOST] = pc.endpoint.host
            it[PORT] = pc.endpoint.port
            it[TRANSPORT] = pc.endpoint.transport.name
            val fingerprint = pc.endpoint.keyFingerprint
            if (fingerprint == null) it.remove(KEY_FINGERPRINT) else it[KEY_FINGERPRINT] = fingerprint
            it[BRIDGE_ID] = pc.bridgeId
            it[IV] = iv
            it[TOKEN] = ciphertext
            val expiresAt = pc.expiresAt
            if (expiresAt == null) it.remove(EXPIRES_AT) else it[EXPIRES_AT] = expiresAt.toString()
        }
    }

    suspend fun clear() {
        store.edit {
            val clientId = it[CLIENT_ID]
            it.clear()
            if (clientId != null) it[CLIENT_ID] = clientId
        }
    }

    private companion object {
        val HOST = stringPreferencesKey("host")
        val PORT = intPreferencesKey("port")
        val TRANSPORT = stringPreferencesKey("transport")
        val KEY_FINGERPRINT = stringPreferencesKey("bridge_key_fingerprint")
        val BRIDGE_ID = stringPreferencesKey("bridge_id")
        val IV = stringPreferencesKey("token_iv")
        val TOKEN = stringPreferencesKey("token_sealed")
        val EXPIRES_AT = stringPreferencesKey("token_expires_at")
        val CLIENT_ID = stringPreferencesKey("client_id")
    }
}
