package dev.nemeyes.ncarousel.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores server settings using [EncryptedSharedPreferences] as described in
 * [Android data-at-rest](https://developer.android.com/topic/security/data).
 */
class NextcloudCredentialsStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var serverBaseUrl: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_SERVER, value.trimEnd('/')).apply() }

    var username: String
        get() = prefs.getString(KEY_USER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_USER, value).apply() }

    var password: String
        get() = prefs.getString(KEY_PASS, "") ?: ""
        set(value) { prefs.edit().putString(KEY_PASS, value).apply() }

    /** Remote folder path under the user root, e.g. `Photos` or `Pictures/Vacation` (no leading slash). */
    var remoteFolder: String
        get() = prefs.getString(KEY_FOLDER, DEFAULT_FOLDER) ?: DEFAULT_FOLDER
        set(value) {
            val normalized = value.trim().trim('/').ifEmpty { DEFAULT_FOLDER }
            prefs.edit().putString(KEY_FOLDER, normalized).apply()
        }

    companion object {
        private const val PREFS_NAME = "ncarousel_secure_prefs"
        private const val KEY_SERVER = "server"
        private const val KEY_USER = "username"
        private const val KEY_PASS = "password"
        private const val KEY_FOLDER = "remote_folder"
        private const val DEFAULT_FOLDER = "Photos"
    }
}
