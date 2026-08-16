@file:Suppress("DEPRECATION")

package com.uzairansar.hermex.data.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.uzairansar.hermex.core.network.HermesJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SecretStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clearPrefix(prefix: String)
    fun update(transform: (Map<String, String>) -> Map<String, String>)
}

class AndroidSecretStore(context: Context) : SecretStore {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var secrets: Map<String, String> = loadSecretsOrMigrate()

    @Synchronized
    override fun getString(key: String): String? = secrets[key]

    @Synchronized
    override fun putString(key: String, value: String) {
        persist(secrets + (key to value))
    }

    @Synchronized
    override fun remove(key: String) {
        if (key !in secrets) return
        persist(secrets - key)
    }

    @Synchronized
    override fun clearPrefix(prefix: String) {
        val updated = secrets.filterKeys { !it.startsWith(prefix) }
        if (updated.size != secrets.size) persist(updated)
    }

    @Synchronized
    override fun update(transform: (Map<String, String>) -> Map<String, String>) {
        persist(transform(secrets))
    }

    private fun loadSecretsOrMigrate(): Map<String, String> {
        preferences.getString(PAYLOAD_KEY, null)?.let(::decryptPayload)?.let { return it }
        val migrated = migrateLegacyPreferences()
        if (migrated.isNotEmpty()) {
            persist(migrated)
            clearLegacyStorage()
        }
        return migrated
    }

    private fun persist(updated: Map<String, String>) {
        val payload = encryptPayload(updated)
        check(preferences.edit().putString(PAYLOAD_KEY, payload).commit()) {
            "Could not persist secure setting."
        }
        secrets = updated
    }

    private fun encryptPayload(values: Map<String, String>): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        cipher.updateAAD(AAD)
        val ciphertext = cipher.doFinal(HermesJson.encodeToString(values).toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, ciphertext)
            .joinToString(PAYLOAD_SEPARATOR) { bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
    }

    private fun decryptPayload(payload: String): Map<String, String> {
        val parts = payload.split(PAYLOAD_SEPARATOR, limit = 2)
        require(parts.size == 2) { "Secure storage payload is malformed." }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(AAD)
        return HermesJson.decodeFromString(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun migrateLegacyPreferences(): Map<String, String> {
        val legacyFile = File(appContext.applicationInfo.dataDir, "shared_prefs/$LEGACY_PREFERENCES_NAME.xml")
        if (!legacyFile.isFile) return emptyMap()
        return createLegacyPreferences().all.mapNotNull { (key, value) ->
            (value as? String)?.let { key to it }
        }.toMap()
    }

    private fun clearLegacyStorage() {
        runCatching { appContext.deleteSharedPreferences(LEGACY_PREFERENCES_NAME) }
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(LEGACY_KEY_ALIAS)) keyStore.deleteEntry(LEGACY_KEY_ALIAS)
        }
    }

    @Suppress("DEPRECATION")
    private fun createLegacyPreferences(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        LEGACY_PREFERENCES_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    companion object {
        private const val PREFERENCES_NAME = "hermex_secure_v2"
        private const val LEGACY_PREFERENCES_NAME = "hermex_secure"
        private const val PAYLOAD_KEY = "encrypted_payload"
        private const val KEY_ALIAS = "hermex_secure_v2_key"
        private const val LEGACY_KEY_ALIAS = "_androidx_security_master_key_"
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PAYLOAD_SEPARATOR = "."
        private const val GCM_TAG_BITS = 128
        private val AAD = "Hermex Android secure storage v2".toByteArray(Charsets.UTF_8)

        fun reset(context: Context) {
            val appContext = context.applicationContext
            appContext.deleteSharedPreferences(PREFERENCES_NAME)
            appContext.deleteSharedPreferences(LEGACY_PREFERENCES_NAME)
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            listOf(KEY_ALIAS, LEGACY_KEY_ALIAS).forEach { alias ->
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            }
        }
    }
}

class UnavailableSecretStore(
    private val cause: Throwable,
) : SecretStore {
    override fun getString(key: String): String? = null

    override fun putString(key: String, value: String): Nothing = unavailable()

    override fun remove(key: String): Nothing = unavailable()

    override fun clearPrefix(prefix: String): Nothing = unavailable()

    override fun update(transform: (Map<String, String>) -> Map<String, String>): Nothing = unavailable()

    private fun unavailable(): Nothing = throw IllegalStateException(
        "Secure storage is unavailable. Reset secure data from the recovery screen.",
        cause,
    )
}
