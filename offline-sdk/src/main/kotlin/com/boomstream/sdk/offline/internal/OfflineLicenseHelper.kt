package com.boomstream.sdk.offline.internal

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64

/**
 * Persistent store for offline DRM keyset material.
 *
 * **Current scope:** DRM-protected offline is planned for a future release. This class exists
 * now to satisfy the CSO keyset-persistence constraint — keyset bytes, when they are stored, MUST use
 * [EncryptedSharedPreferences] (Jetpack Security). Plain `SharedPreferences`, plain files, or
 * external-storage writes are not acceptable because they are readable without root on rooted
 * devices.
 *
 * **CSO keyset-persistence constraint:** all keyset persistence goes through [EncryptedSharedPreferences]
 * backed by the Android Keystore. The master key never leaves the hardware security module on
 * devices that support it (API 23+ with a secure element or StrongBox).
 *
 * When DRM support lands, this class will be expanded with Widevine offline-license methods.
 * The storage contract (EncryptedSharedPreferences + per-mediaCode key) is locked in now so
 * existing installs do not require a migration when DRM is added.
 *
 * @param context Application context. Used once during construction to open the preference file.
 */
internal class OfflineLicenseHelper(context: Context) {

    private val prefs: android.content.SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /**
     * Persists DRM keyset bytes for [mediaCode].
     *
     * The bytes are Base64-encoded for storage compatibility with [EncryptedSharedPreferences].
     * The preference file itself is AES-256-GCM encrypted at rest.
     *
     * @param mediaCode Boomstream media code used as the storage key.
     * @param keysetBytes Raw keyset material (e.g. a serialised Widevine offline license).
     */
    fun storeKeyset(mediaCode: String, keysetBytes: ByteArray) {
        prefs.edit()
            .putString(prefsKey(mediaCode), Base64.encodeToString(keysetBytes, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Loads DRM keyset bytes for [mediaCode], or `null` if nothing has been stored.
     */
    fun loadKeyset(mediaCode: String): ByteArray? {
        val encoded = prefs.getString(prefsKey(mediaCode), null) ?: return null
        return runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull()
    }

    /**
     * Removes the stored keyset for [mediaCode].
     *
     * Should be called from [com.boomstream.sdk.offline.BoomstreamOfflineManager.deleteDownload]
     * so keyset material is wiped alongside the media segments.
     */
    fun removeKeyset(mediaCode: String) {
        prefs.edit().remove(prefsKey(mediaCode)).apply()
    }

    /** Wipes all stored keysets. Called from [BoomstreamOfflineManager.deleteAllDownloads]. */
    fun removeAllKeysets() {
        prefs.edit().clear().apply()
    }

    private fun prefsKey(mediaCode: String) = "keyset_$mediaCode"

    private companion object {
        const val PREFS_FILE_NAME = "boomstream_offline_license"
    }
}
