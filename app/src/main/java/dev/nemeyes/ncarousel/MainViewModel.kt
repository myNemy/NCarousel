package dev.nemeyes.ncarousel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.CarouselStatusNotifications
import dev.nemeyes.ncarousel.data.HttpClientProvider
import dev.nemeyes.ncarousel.data.ImageListCache
import dev.nemeyes.ncarousel.data.ImageSyncRepository
import dev.nemeyes.ncarousel.data.NextcloudLoginFlowV2
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator
import dev.nemeyes.ncarousel.data.NextcloudWebDavClient
import dev.nemeyes.ncarousel.data.OrderMode
import dev.nemeyes.ncarousel.data.WallpaperDiskCache
import dev.nemeyes.ncarousel.data.WallpaperOrderEngine
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import dev.nemeyes.ncarousel.data.ocs.OcsCapabilitiesClient
import dev.nemeyes.ncarousel.data.ocs.OcsUserClient
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val remoteFolder: String = "",
    val hasActiveAccount: Boolean = false,
    val accounts: List<AccountUi> = emptyList(),
    val activeAccountId: String? = null,
    val orderMode: OrderMode = OrderMode.RANDOM,
    val maxImageSizeMb: Int = 0,
    val maxWallpaperDiskCacheMb: Int = WallpaperDiskCache.DEFAULT_MAX_MB,
    val autoWallpaperEnabled: Boolean = false,
    val autoIntervalMinutes: Int = 30,
    val showStatusNotifications: Boolean = true,
    /** First launch: show consent dialog and request notification permission where required. */
    val needsInitialConsent: Boolean = false,
    val busy: Boolean = false,
    val statusMessage: String? = null,
    val imageHrefs: List<String> = emptyList(),
    /** OCS capabilities.theming.color (hex); drives [NCarouselTheme]. */
    val instanceThemingPrimaryHex: String? = null,
    /** OCS capabilities.theming.color-text. */
    val instanceThemingOnPrimaryHex: String? = null,
)

data class AccountUi(
    val id: String,
    val label: String,
)

sealed interface UiEvent {
    data class OpenUrl(val url: String) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val accounts = NextcloudAccountStore(application)
    private val carousel = CarouselPreferences(application)
    private val syncRepo = ImageSyncRepository(application)
    private val http = HttpClientProvider.create(application)

    private fun activeOrNull() = accounts.getActiveAccount()
    private fun accountsUi(): List<AccountUi> =
        accounts.getAccounts().map { a ->
            AccountUi(
                id = a.id,
                label = "${a.userId} @ ${a.serverBaseUrl}",
            )
        }

    private val _ui = MutableStateFlow(
        run {
            val acc0 = activeOrNull()
            MainUiState(
                serverUrl = acc0?.serverBaseUrl.orEmpty(),
                username = acc0?.userId.orEmpty(),
                password = acc0?.appPassword.orEmpty(),
                remoteFolder = acc0?.remoteFolder.orEmpty(),
                hasActiveAccount = acc0 != null,
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                orderMode = carousel.orderMode,
                maxImageSizeMb = carousel.maxImageSizeMb,
                maxWallpaperDiskCacheMb = carousel.maxWallpaperDiskCacheMb,
                autoWallpaperEnabled = carousel.autoWallpaperEnabled,
                autoIntervalMinutes = carousel.autoIntervalMinutes,
                showStatusNotifications = carousel.showStatusNotifications,
                needsInitialConsent = !carousel.initialConsentFlowCompleted,
                instanceThemingPrimaryHex = acc0?.let { carousel.getThemingPrimaryHex(it.id) },
                instanceThemingOnPrimaryHex = acc0?.let { carousel.getThemingOnPrimaryHex(it.id) },
            )
        },
    )
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            refreshInstanceThemingFromNetwork()
        }
    }

    private suspend fun refreshInstanceThemingFromNetwork() {
        val acc = activeOrNull() ?: return
        OcsCapabilitiesClient(http).fetchTheming(
            acc.serverBaseUrl,
            acc.loginName,
            acc.appPassword,
        ).onSuccess { t ->
            carousel.setThemingForAccount(acc.id, t.color, t.colorText)
            if (activeOrNull()?.id == acc.id) {
                _ui.update {
                    it.copy(
                        instanceThemingPrimaryHex = t.color,
                        instanceThemingOnPrimaryHex = t.colorText,
                    )
                }
            }
        }
    }

    private fun scheduleThemingRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshInstanceThemingFromNetwork()
        }
    }

    fun updateServerUrl(value: String) = _ui.update { it.copy(serverUrl = value) }
    fun updateUsername(value: String) = _ui.update { it.copy(username = value) }
    fun updatePassword(value: String) = _ui.update { it.copy(password = value) }
    fun updateRemoteFolder(value: String) = _ui.update { it.copy(remoteFolder = value) }
    fun updateOrderMode(value: OrderMode) = _ui.update { it.copy(orderMode = value) }
    fun updateMaxImageSizeMbText(value: String) = _ui.update {
        it.copy(maxImageSizeMb = value.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0)
    }
    fun updateMaxWallpaperDiskCacheMbText(value: String) = _ui.update {
        val n = value.filter { ch -> ch.isDigit() }.toIntOrNull() ?: WallpaperDiskCache.DEFAULT_MAX_MB
        it.copy(maxWallpaperDiskCacheMb = n.coerceIn(WallpaperDiskCache.MIN_MB, WallpaperDiskCache.MAX_MB))
    }
    fun updateAutoWallpaperEnabled(value: Boolean) = _ui.update { it.copy(autoWallpaperEnabled = value) }
    fun updateAutoIntervalMinutesText(value: String) = _ui.update {
        it.copy(
            autoIntervalMinutes = value.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceAtLeast(
                WallpaperWorkScheduler.MIN_INTERVAL_MINUTES,
            ) ?: WallpaperWorkScheduler.MIN_INTERVAL_MINUTES,
        )
    }

    fun updateShowStatusNotifications(enabled: Boolean) {
        carousel.showStatusNotifications = enabled
        _ui.update { it.copy(showStatusNotifications = enabled) }
    }

    /** Call after first-launch dialog is dismissed or notification permission result is applied. */
    fun completeInitialConsentFlow() {
        carousel.initialConsentFlowCompleted = true
        _ui.update { it.copy(needsInitialConsent = false) }
    }

    fun saveCredentials() {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _ui.update { it.copy(statusMessage = "Compila URL, utente e password.") }
            return
        }
        // Manual account: assume provided username is UID for WebDAV.
        val acc = dev.nemeyes.ncarousel.data.accounts.NextcloudAccount(
            serverBaseUrl = s.serverUrl,
            userId = s.username,
            loginName = s.username,
            appPassword = s.password,
            remoteFolder = s.remoteFolder.ifBlank { "Photos" },
        )
        accounts.upsert(acc)
        accounts.setActiveAccountId(acc.id)
        WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
        _ui.update {
            it.copy(
                hasActiveAccount = true,
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                statusMessage = "Account salvato (cifrato sul dispositivo).",
                instanceThemingPrimaryHex = carousel.getThemingPrimaryHex(acc.id),
                instanceThemingOnPrimaryHex = carousel.getThemingOnPrimaryHex(acc.id),
            )
        }
        scheduleThemingRefresh()
    }

    fun startNextcloudLoginV2() {
        val s = _ui.value
        if (s.serverUrl.isBlank()) {
            _ui.update { it.copy(statusMessage = "Compila URL server.") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = "Avvio login Nextcloud…") }
            val flow = NextcloudLoginFlowV2(http)
            val started = flow.start(s.serverUrl)
            started.fold(
                onSuccess = { start ->
                    _events.tryEmit(UiEvent.OpenUrl(start.loginUrl))
                    _ui.update { it.copy(statusMessage = "Completa il login nel browser…") }
                    val polled = flow.pollUntilDone(start.pollEndpoint, start.pollToken)
                    polled.fold(
                        onSuccess = { ok ->
                            val userId = OcsUserClient(http).fetchUserId(ok.server, ok.loginName, ok.appPassword)
                                .getOrElse { ok.loginName }
                            val acc = dev.nemeyes.ncarousel.data.accounts.NextcloudAccount(
                                serverBaseUrl = ok.server,
                                userId = userId,
                                loginName = ok.loginName,
                                appPassword = ok.appPassword,
                                remoteFolder = "Photos",
                            )
                            accounts.upsert(acc)
                            accounts.setActiveAccountId(acc.id)
                            _ui.update {
                                val active = accounts.getActiveAccount()
                                it.copy(
                                    busy = false,
                                    hasActiveAccount = true,
                                    accounts = accountsUi(),
                                    activeAccountId = accounts.getActiveAccountId(),
                                    serverUrl = active?.serverBaseUrl.orEmpty(),
                                    username = active?.userId.orEmpty(),
                                    password = active?.appPassword.orEmpty(),
                                    remoteFolder = active?.remoteFolder.orEmpty(),
                                    statusMessage = "Login completato: credenziali salvate (app password).",
                                    instanceThemingPrimaryHex = active?.let { a -> carousel.getThemingPrimaryHex(a.id) },
                                    instanceThemingOnPrimaryHex = active?.let { a -> carousel.getThemingOnPrimaryHex(a.id) },
                                )
                            }
                            WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
                            scheduleThemingRefresh()
                        },
                        onFailure = { e ->
                            _ui.update {
                                it.copy(
                                    busy = false,
                                    statusMessage = "Login fallito: ${e.message ?: e.javaClass.simpleName}",
                                )
                            }
                        },
                    )
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            statusMessage = "Impossibile avviare login: ${e.message ?: e.javaClass.simpleName}",
                        )
                    }
                },
            )
        }
    }

    fun saveCarouselOptions() {
        val s = _ui.value
        carousel.orderMode = s.orderMode
        carousel.maxImageSizeMb = s.maxImageSizeMb
        carousel.maxWallpaperDiskCacheMb = s.maxWallpaperDiskCacheMb
        carousel.autoWallpaperEnabled = s.autoWallpaperEnabled
        carousel.autoIntervalMinutes = s.autoIntervalMinutes.coerceAtLeast(WallpaperWorkScheduler.MIN_INTERVAL_MINUTES)
        activeOrNull()?.let { a ->
            val folder = s.remoteFolder.trim().trim('/').ifBlank {
                a.remoteFolder.ifBlank { "Photos" }
            }
            accounts.upsert(a.copy(remoteFolder = folder))
            WallpaperDiskCache(getApplication(), a.id, carousel.maxWallpaperDiskCacheMb).enforceBudget()
        }
        WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
        _ui.update { it.copy(statusMessage = "Opzioni carosello salvate.") }
    }

    fun clearWallpaperDiskCache() {
        val id = accounts.getActiveAccountId()
        if (id == null) {
            _ui.update { it.copy(statusMessage = "Nessun account attivo.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            WallpaperDiskCache.clear(getApplication(), id)
            _ui.update { it.copy(statusMessage = "Cache immagini sul disco svuotata (account attivo).") }
        }
    }

    fun clearStatus() = _ui.update { it.copy(statusMessage = null) }

    fun testConnection() {
        val s = _ui.value
        val active = activeOrNull()
        if (active == null) {
            _ui.update { it.copy(statusMessage = "Aggiungi un account (login o credenziali).") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = null) }
            val client = NextcloudWebDavClient(
                http,
                active.serverBaseUrl,
                active.userId,
                active.loginName,
                active.appPassword,
            )
            val result = client.verifyReachable()
            _ui.update {
                it.copy(
                    busy = false,
                    statusMessage = result.fold(
                        onSuccess = { "Connessione WebDAV OK." },
                        onFailure = { e -> "Errore: ${e.message ?: e.javaClass.simpleName}" },
                    ),
                )
            }
        }
    }

    fun refreshImageList() {
        val s = _ui.value
        val active = activeOrNull()
        if (active == null) {
            _ui.update { it.copy(statusMessage = "Aggiungi un account (login o credenziali).") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = "Scansione cartelle in corso…") }
            // Use the folder shown in the form, not only the last saved value (login v2 defaults to Photos).
            val folder = s.remoteFolder.trim().trim('/').ifBlank {
                active.remoteFolder.ifBlank { "Photos" }
            }
            val accountForSync = active.copy(remoteFolder = folder)
            accounts.upsert(accountForSync)
            val maxBytes = s.maxImageSizeMb.toLong() * 1024L * 1024L
            val result = syncRepo.syncFromServer(http, accountForSync, maxBytes)
            result.fold(
                onSuccess = { list ->
                    val fp = WallpaperOrderEngine.libraryFingerprint(list)
                    WallpaperOrderEngine(getApplication(), active.id).onLibraryFingerprintChanged(fp)
                    ImageListCache(getApplication(), active.id).write(list)
                    _ui.update {
                        it.copy(
                            busy = false,
                            imageHrefs = list,
                            remoteFolder = folder,
                            statusMessage = "Trovate ${list.size} immagini.",
                        )
                    }
                    val app = getApplication<Application>()
                    withContext(Dispatchers.IO) {
                        CarouselStatusNotifications.maybeShowListRefreshed(app, carousel, list.size)
                    }
                },
                onFailure = { e ->
                    _ui.update {
                        it.copy(
                            busy = false,
                            imageHrefs = emptyList(),
                            statusMessage = "Errore elenco: ${e.message ?: e.javaClass.simpleName}",
                        )
                    }
                },
            )
        }
    }

    fun applyNextWallpaper() {
        val s = _ui.value
        if (s.imageHrefs.isEmpty()) {
            _ui.update { it.copy(statusMessage = "Aggiorna prima l’elenco immagini.") }
            return
        }
        if (activeOrNull() == null) {
            _ui.update { it.copy(statusMessage = "Aggiungi un account (login o credenziali).") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, statusMessage = "Download in corso…") }
            val err = withContext(Dispatchers.IO) {
                NextWallpaperApplicator.applyNext(getApplication(), s.orderMode)
            }
            _ui.update {
                it.copy(
                    busy = false,
                    statusMessage = when {
                        err == null -> "Sfondo aggiornato."
                        else -> err
                    },
                )
            }
        }
    }

    fun loadCachedListIfAny() {
        val active = activeOrNull() ?: return
        viewModelScope.launch {
            val legacy = ImageListCache(getApplication(), active.id).read()
            if (legacy.isNotEmpty()) {
                _ui.update { it.copy(imageHrefs = legacy) }
                return@launch
            }
            val cached = syncRepo.readCachedHrefs(active.id)
            if (cached.isNotEmpty()) {
                _ui.update { it.copy(imageHrefs = cached) }
            }
        }
    }

    fun setActiveAccount(id: String) {
        accounts.setActiveAccountId(id)
        WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
        val a = accounts.getActiveAccount()
        _ui.update {
            it.copy(
                activeAccountId = id,
                hasActiveAccount = a != null,
                serverUrl = a?.serverBaseUrl.orEmpty(),
                username = a?.userId.orEmpty(),
                password = a?.appPassword.orEmpty(),
                remoteFolder = a?.remoteFolder.orEmpty(),
                imageHrefs = emptyList(),
                statusMessage = "Account attivo aggiornato.",
                instanceThemingPrimaryHex = a?.let { carousel.getThemingPrimaryHex(it.id) },
                instanceThemingOnPrimaryHex = a?.let { carousel.getThemingOnPrimaryHex(it.id) },
            )
        }
        loadCachedListIfAny()
        scheduleThemingRefresh()
    }

    fun deleteAccount(id: String) {
        carousel.clearThemingForAccount(id)
        accounts.delete(id)
        val a = accounts.getActiveAccount()
        _ui.update {
            it.copy(
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                hasActiveAccount = a != null,
                serverUrl = a?.serverBaseUrl.orEmpty(),
                username = a?.userId.orEmpty(),
                password = a?.appPassword.orEmpty(),
                remoteFolder = a?.remoteFolder.orEmpty(),
                imageHrefs = emptyList(),
                statusMessage = "Account rimosso.",
                instanceThemingPrimaryHex = a?.let { carousel.getThemingPrimaryHex(it.id) },
                instanceThemingOnPrimaryHex = a?.let { carousel.getThemingOnPrimaryHex(it.id) },
            )
        }
        loadCachedListIfAny()
        scheduleThemingRefresh()
    }
}
