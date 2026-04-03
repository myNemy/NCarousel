package dev.nemeyes.ncarousel.data.accounts

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.nemeyes.ncarousel.data.NextcloudCredentialsStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted multi-account store.
 *
 * Migrates legacy single-account values from [NextcloudCredentialsStore] on first run.
 */
class NextcloudAccountStore(context: Context) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        app,
        PREFS_NAME,
        MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    init {
        migrateIfNeeded()
    }

    fun getAccounts(): List<NextcloudAccount> {
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        NextcloudAccount(
                            id = o.getString("id"),
                            serverBaseUrl = o.getString("serverBaseUrl"),
                            userId = o.getString("userId"),
                            loginName = o.optString("loginName", o.getString("userId")),
                            appPassword = o.getString("appPassword"),
                            remoteFolder = o.optString("remoteFolder", "Photos"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun getActiveAccountId(): String? = prefs.getString(KEY_ACTIVE_ID, null)

    fun getActiveAccount(): NextcloudAccount? {
        val id = getActiveAccountId() ?: return null
        return getAccounts().firstOrNull { it.id == id }
    }

    fun setActiveAccountId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_ID, id).apply()
    }

    fun upsert(account: NextcloudAccount) {
        val list = getAccounts().toMutableList()
        val idx = list.indexOfFirst { it.id == account.id }
        if (idx >= 0) list[idx] = account else list.add(account)
        writeAccounts(list)
        if (getActiveAccountId() == null) setActiveAccountId(account.id)
    }

    fun delete(accountId: String) {
        val list = getAccounts().filterNot { it.id == accountId }
        writeAccounts(list)
        if (getActiveAccountId() == accountId) {
            setActiveAccountId(list.firstOrNull()?.id)
        }
    }

    fun updateRemoteFolder(accountId: String, remoteFolder: String) {
        val normalized = remoteFolder.trim().trim('/').ifEmpty { "Photos" }
        val list = getAccounts().map {
            if (it.id == accountId) it.copy(remoteFolder = normalized) else it
        }
        writeAccounts(list)
    }

    private fun writeAccounts(list: List<NextcloudAccount>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("serverBaseUrl", a.serverBaseUrl.trimEnd('/'))
                    .put("userId", a.userId)
                    .put("loginName", a.loginName)
                    .put("appPassword", a.appPassword)
                    .put("remoteFolder", a.remoteFolder),
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, arr.toString()).apply()
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val legacy = NextcloudCredentialsStore(app)
        val server = legacy.serverBaseUrl
        val user = legacy.username
        val pass = legacy.password
        val folder = legacy.remoteFolder
        if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            // Legacy didn't distinguish loginName vs userId; assume user is UID.
            val acc = NextcloudAccount(
                serverBaseUrl = server,
                userId = user,
                loginName = user,
                appPassword = pass,
                remoteFolder = folder.ifBlank { "Photos" },
            )
            upsert(acc)
            setActiveAccountId(acc.id)
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    companion object {
        private const val PREFS_NAME = "ncarousel_accounts_secure"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_ACTIVE_ID = "active_account_id"
        private const val KEY_MIGRATED = "migrated_from_legacy"
    }
}

