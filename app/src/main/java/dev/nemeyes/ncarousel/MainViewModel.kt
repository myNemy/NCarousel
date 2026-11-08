package dev.nemeyes.ncarousel

import android.app.Application
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import dev.nemeyes.ncarousel.data.BatteryOptimizationHelper
import dev.nemeyes.ncarousel.data.CarouselPreferences
import dev.nemeyes.ncarousel.data.CarouselStatusNotifications
import dev.nemeyes.ncarousel.data.CropOffset
import dev.nemeyes.ncarousel.data.CropOffsetStore
import dev.nemeyes.ncarousel.data.ExcludedHrefStore
import dev.nemeyes.ncarousel.data.HttpClientProvider
import dev.nemeyes.ncarousel.data.ImageExifSummary
import dev.nemeyes.ncarousel.data.ImageListCache
import dev.nemeyes.ncarousel.data.ImageShareHelper
import dev.nemeyes.ncarousel.data.ImageSyncRepository
import dev.nemeyes.ncarousel.data.LastAppliedWallpaperStore
import dev.nemeyes.ncarousel.data.NextcloudLoginFlowV2
import dev.nemeyes.ncarousel.data.NextWallpaperApplicator
import dev.nemeyes.ncarousel.data.NextcloudDavAccess
import dev.nemeyes.ncarousel.data.NextcloudDavAccessFactory
import dev.nemeyes.ncarousel.data.NextcloudWebDavClient
import dev.nemeyes.ncarousel.data.GeocoderOrderMode
import dev.nemeyes.ncarousel.data.OrderMode
import dev.nemeyes.ncarousel.data.WallpaperCropMode
import dev.nemeyes.ncarousel.data.WallpaperDiskCache
import dev.nemeyes.ncarousel.data.WallpaperOrderEngine
import dev.nemeyes.ncarousel.data.WallpaperTarget
import dev.nemeyes.ncarousel.data.parseDavLastModifiedEpochMs
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccount
import dev.nemeyes.ncarousel.data.accounts.NextcloudAccountStore
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.ocs.OcsCapabilitiesClient
import dev.nemeyes.ncarousel.data.ocs.OcsUserClient
import dev.nemeyes.ncarousel.work.HomeWallpaperResync
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val serverUrl: String = "",
    val username: String = "",
    /** Login identifier used for Basic Auth (can differ from UID). */
    val loginName: String = "",
    val password: String = "",
    val remoteFolder: String = "",
    val hasActiveAccount: Boolean = false,
    /** Active account uses Nextcloud Files SSO (Library previews must not use Basic Auth). */
    val accountUsesSso: Boolean = false,
    /** Android Account Manager name when [accountUsesSso] is true. */
    val ssoAccountName: String? = null,
    val accounts: List<AccountUi> = emptyList(),
    val activeAccountId: String? = null,
    val orderMode: OrderMode = OrderMode.RANDOM,
    val wallpaperTarget: WallpaperTarget = WallpaperTarget.HOME_AND_LOCK,
    val wallpaperCropMode: WallpaperCropMode = WallpaperCropMode.COVER,
    val maxImageSizeMb: Int = 0,
    val maxWallpaperDiskCacheMb: Int = WallpaperDiskCache.DEFAULT_MAX_MB,
    val autoWallpaperEnabled: Boolean = false,
    val autoWallpaperUnmeteredOnly: Boolean = false,
    val autoIntervalMinutes: Int = 30,
    val showStatusNotifications: Boolean = true,
    val notifyWallpaperApplied: Boolean = true,
    val notifyLibraryRefreshed: Boolean = true,
    val notifyWallpaperIncludeLocation: Boolean = true,
    val geocoderNominatimEnabled: Boolean = true,
    val geocoderPlatformEnabled: Boolean = true,
    val geocoderPhotonEnabled: Boolean = true,
    val geocoderOrderMode: GeocoderOrderMode = GeocoderOrderMode.NOMINATIM_FIRST,
    /** First launch: show consent dialog and request notification permission where required. */
    val needsInitialConsent: Boolean = false,
    val busy: Boolean = false,
    val statusMessage: String? = null,
    val imageHrefs: List<String> = emptyList(),
    /** href -> Nextcloud fileId (for server-side previews), when available. */
    val imageFileIds: Map<String, Long> = emptyMap(),
    /** href -> WebDAV getlastmodified as epoch ms (Library date sort), when parseable. */
    val imageLastModifiedEpochMs: Map<String, Long> = emptyMap(),
    /** href -> WebDAV getcontentlength (Library size label), when known. */
    val imageContentLengthBytes: Map<String, Long> = emptyMap(),
    /** href -> 1-based carousel index ("Image X of Y") matching notifications (active/non-excluded only). */
    val imageCarouselIndexByHref: Map<String, Int> = emptyMap(),
    /** WebDAV hrefs excluded from automatic carousel picks (still shown in Library). */
    val excludedImageHrefs: Set<String> = emptySet(),
    /** OCS capabilities.theming.color (hex); drives [NCarouselTheme]. */
    val instanceThemingPrimaryHex: String? = null,
    /** OCS capabilities.theming.color-text. */
    val instanceThemingOnPrimaryHex: String? = null,
    /**
     * API 23+: true se l’app non è esclusa dalle ottimizzazioni batteria (il lavoro in background può subire ritardi).
     */
    val batteryOptimizationMayDelayWork: Boolean = false,
    /** Dialog di consenso prima di aprire le impostazioni di esclusione batteria. */
    val batteryOptimizationConsentVisible: Boolean = false,
    /** Nome file (path remoto) dell’ultimo sfondo applicato da NCarousel per l’account attivo. */
    val lastWallpaperFileLabel: String? = null,
    /** Full WebDAV href of the last wallpaper applied by NCarousel (Library highlight). */
    val lastWallpaperHref: String? = null,
    /**
     * Folder path (Nextcloud "Files" relative) of the last wallpaper applied by NCarousel.
     * Example: `Photos/mie/italia/altro/`
     */
    val lastWallpaperFolderPath: String? = null,
    /** Place label as computed when the wallpaper was applied (notifications geocoder). */
    val lastWallpaperPlaceLabel: String? = null,
    val wallpaperExifLines: List<String> = emptyList(),
    val wallpaperExifLoading: Boolean = false,
    val wallpaperExifError: String? = null,
)

data class AccountUi(
    val id: String,
    val label: String,
)

sealed interface UiEvent {
    data class OpenUrl(val url: String) : UiEvent
    data object RequestIgnoreBatteryOptimizations : UiEvent
    /** Launch Nextcloud Files SSO account picker ([com.nextcloud.android.sso.ImportSsoAccount]). */
    data object RequestSsoImport : UiEvent
    /**
     * Refresh found no images under [folderLabel] (blank / default → Photos).
     * UI may offer the remote folder browser.
     */
    data class SuggestBrowseRemoteFolder(val folderLabel: String) : UiEvent
    /** Content URI from [ImageShareHelper] for ACTION_SEND (no Nextcloud credentials). */
    data class ShareImage(
        val uri: android.net.Uri,
        val mimeType: String,
        val chooserTitle: String,
    ) : UiEvent
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private fun appStr(@StringRes id: Int, vararg args: Any): String {
        val app = getApplication<Application>()
        @Suppress("SpreadOperator")
        return if (args.isEmpty()) app.getString(id) else app.getString(id, *args)
    }

    private val accounts = NextcloudAccountStore(application)
    private val carousel = CarouselPreferences(application)
    private val syncRepo = ImageSyncRepository(application)
    private val http = HttpClientProvider.create(application)

    private suspend fun computeCarouselIndicesForActiveAccount(
        hrefs: List<String>,
        mode: OrderMode,
        excluded: Set<String> = emptySet(),
    ): Map<String, Int> = withContext(Dispatchers.IO) {
        val accId = accounts.getActiveAccountId() ?: return@withContext emptyMap()
        val active = ExcludedHrefStore.filterActive(hrefs, excluded)
        WallpaperOrderEngine(getApplication(), accId).carouselIndexByHref(active, mode)
    }

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
                loginName = acc0?.loginName.orEmpty(),
                password = acc0?.appPassword.orEmpty(),
                remoteFolder = acc0?.remoteFolder.orEmpty(),
                hasActiveAccount = acc0 != null,
                accountUsesSso = acc0?.usesSso == true,
                ssoAccountName = acc0?.ssoAccountName,
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                orderMode = carousel.orderMode,
                wallpaperTarget = carousel.wallpaperTarget,
                wallpaperCropMode = carousel.wallpaperCropMode,
                maxImageSizeMb = carousel.maxImageSizeMb,
                maxWallpaperDiskCacheMb = carousel.maxWallpaperDiskCacheMb,
                autoWallpaperEnabled = carousel.autoWallpaperEnabled,
                autoWallpaperUnmeteredOnly = carousel.autoWallpaperUnmeteredOnly,
                autoIntervalMinutes = carousel.autoIntervalMinutes,
                showStatusNotifications = carousel.showStatusNotifications,
                notifyWallpaperApplied = carousel.notifyWallpaperApplied,
                notifyLibraryRefreshed = carousel.notifyLibraryRefreshed,
                notifyWallpaperIncludeLocation = carousel.notifyWallpaperIncludeLocation,
                geocoderNominatimEnabled = carousel.geocoderNominatimEnabled,
                geocoderPlatformEnabled = carousel.geocoderPlatformEnabled,
                geocoderPhotonEnabled = carousel.geocoderPhotonEnabled,
                geocoderOrderMode = carousel.geocoderOrderMode,
                needsInitialConsent = !carousel.initialConsentFlowCompleted,
                instanceThemingPrimaryHex = acc0?.let { carousel.getThemingPrimaryHex(it.id) },
                instanceThemingOnPrimaryHex = acc0?.let { carousel.getThemingOnPrimaryHex(it.id) },
                batteryOptimizationMayDelayWork =
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        BatteryOptimizationHelper.shouldPromptForExemption(getApplication()),
            )
        },
    )
    val ui: StateFlow<MainUiState> = _ui.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private enum class BatteryOptConsentTrigger { EnableAutoSwitch, SettingsBanner }

    private var batteryOptConsentTrigger: BatteryOptConsentTrigger? = null

    /** Prevents a cancelled/slow job from clearing [MainUiState.busy] while a newer one runs. */
    private var busyToken = 0
    private var loginJob: Job? = null
    private var refreshListJob: Job? = null

    private fun beginBusy(): Int {
        val token = ++busyToken
        _ui.update { it.copy(busy = true) }
        return token
    }

    private fun endBusy(token: Int) {
        if (busyToken == token) {
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun clearBusyState() {
        busyToken++
        _ui.update { it.copy(busy = false) }
    }

    init {
        refreshBatteryOptimizationStatus()
        viewModelScope.launch(Dispatchers.IO) {
            refreshInstanceThemingFromNetwork()
        }
    }

    private fun computeBatteryOptimizationMayDelay(app: Application): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            BatteryOptimizationHelper.shouldPromptForExemption(app)

    fun refreshBatteryOptimizationStatus() {
        _ui.update { it.copy(batteryOptimizationMayDelayWork = computeBatteryOptimizationMayDelay(getApplication())) }
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
    fun updateUsername(value: String) = _ui.update { it.copy(username = value, loginName = value) }
    fun updatePassword(value: String) = _ui.update { it.copy(password = value) }
    fun updateRemoteFolder(value: String) = _ui.update { it.copy(remoteFolder = value) }
    fun updateOrderMode(value: OrderMode) = _ui.update { it.copy(orderMode = value) }
    fun updateWallpaperTarget(value: WallpaperTarget) = _ui.update { it.copy(wallpaperTarget = value) }
    fun updateWallpaperCropMode(value: WallpaperCropMode) = _ui.update { it.copy(wallpaperCropMode = value) }
    fun updateMaxImageSizeMbText(value: String) = _ui.update {
        it.copy(maxImageSizeMb = value.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0)
    }
    fun updateMaxWallpaperDiskCacheMbText(value: String) = _ui.update {
        val n = value.filter { ch -> ch.isDigit() }.toIntOrNull() ?: WallpaperDiskCache.DEFAULT_MAX_MB
        it.copy(maxWallpaperDiskCacheMb = n.coerceIn(WallpaperDiskCache.MIN_MB, WallpaperDiskCache.MAX_MB))
    }
    fun updateAutoWallpaperEnabled(value: Boolean) {
        if (value) {
            if (computeBatteryOptimizationMayDelay(getApplication())) {
                batteryOptConsentTrigger = BatteryOptConsentTrigger.EnableAutoSwitch
                _ui.update { it.copy(batteryOptimizationConsentVisible = true) }
                return
            }
            _ui.update { it.copy(autoWallpaperEnabled = true) }
            return
        }
        batteryOptConsentTrigger = null
        _ui.update {
            it.copy(
                autoWallpaperEnabled = false,
                batteryOptimizationConsentVisible = false,
            )
        }
    }

    fun openBatteryOptimizationConsentFromSettings() {
        if (!computeBatteryOptimizationMayDelay(getApplication())) return
        batteryOptConsentTrigger = BatteryOptConsentTrigger.SettingsBanner
        _ui.update { it.copy(batteryOptimizationConsentVisible = true) }
    }

    fun onBatteryOptimizationConsentConfirmed() {
        val trigger = batteryOptConsentTrigger
        batteryOptConsentTrigger = null
        when (trigger) {
            BatteryOptConsentTrigger.EnableAutoSwitch ->
                _ui.update {
                    it.copy(
                        batteryOptimizationConsentVisible = false,
                        autoWallpaperEnabled = true,
                    )
                }
            BatteryOptConsentTrigger.SettingsBanner, null ->
                _ui.update { it.copy(batteryOptimizationConsentVisible = false) }
        }
        _events.tryEmit(UiEvent.RequestIgnoreBatteryOptimizations)
    }

    fun onBatteryOptimizationConsentDismissed() {
        batteryOptConsentTrigger = null
        _ui.update { it.copy(batteryOptimizationConsentVisible = false) }
    }
    fun updateAutoIntervalMinutesText(value: String) = _ui.update {
        it.copy(
            autoIntervalMinutes = value.filter { ch -> ch.isDigit() }.toIntOrNull()?.coerceAtLeast(
                WallpaperWorkScheduler.MIN_INTERVAL_MINUTES,
            ) ?: WallpaperWorkScheduler.MIN_INTERVAL_MINUTES,
        )
    }

    fun updateAutoWallpaperUnmeteredOnly(value: Boolean) =
        _ui.update { it.copy(autoWallpaperUnmeteredOnly = value) }

    fun updateShowStatusNotifications(enabled: Boolean) {
        carousel.showStatusNotifications = enabled
        _ui.update { it.copy(showStatusNotifications = enabled) }
    }

    fun updateNotifyWallpaperApplied(enabled: Boolean) {
        carousel.notifyWallpaperApplied = enabled
        _ui.update { it.copy(notifyWallpaperApplied = enabled) }
    }

    fun updateNotifyLibraryRefreshed(enabled: Boolean) {
        carousel.notifyLibraryRefreshed = enabled
        _ui.update { it.copy(notifyLibraryRefreshed = enabled) }
    }

    fun updateNotifyWallpaperIncludeLocation(enabled: Boolean) {
        carousel.notifyWallpaperIncludeLocation = enabled
        _ui.update { it.copy(notifyWallpaperIncludeLocation = enabled) }
    }

    fun updateGeocoderNominatimEnabled(enabled: Boolean) {
        carousel.geocoderNominatimEnabled = enabled
        _ui.update { it.copy(geocoderNominatimEnabled = enabled) }
    }

    fun updateGeocoderPlatformEnabled(enabled: Boolean) {
        carousel.geocoderPlatformEnabled = enabled
        _ui.update { it.copy(geocoderPlatformEnabled = enabled) }
    }

    fun updateGeocoderPhotonEnabled(enabled: Boolean) {
        carousel.geocoderPhotonEnabled = enabled
        _ui.update { it.copy(geocoderPhotonEnabled = enabled) }
    }

    fun updateGeocoderOrderMode(mode: GeocoderOrderMode) {
        carousel.geocoderOrderMode = mode
        _ui.update { it.copy(geocoderOrderMode = mode) }
    }

    /** Call after first-launch dialog is dismissed or notification permission result is applied. */
    fun completeInitialConsentFlow() {
        carousel.initialConsentFlowCompleted = true
        _ui.update { it.copy(needsInitialConsent = false) }
    }

    fun saveCredentials() {
        val s = _ui.value
        if (s.serverUrl.isBlank() || s.username.isBlank() || s.password.isBlank()) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_fill_url_user_password)) }
            return
        }
        if (!s.serverUrl.trim().startsWith("https://", ignoreCase = true)) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_https_required)) }
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
                accountUsesSso = false,
                ssoAccountName = null,
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                statusMessage = appStr(R.string.msg_account_saved_encrypted),
                instanceThemingPrimaryHex = carousel.getThemingPrimaryHex(acc.id),
                instanceThemingOnPrimaryHex = carousel.getThemingOnPrimaryHex(acc.id),
            )
        }
        scheduleThemingRefresh()
    }

    /** Asks the UI to launch Nextcloud Files SSO account import. */
    fun startNextcloudSsoImport() {
        _events.tryEmit(UiEvent.RequestSsoImport)
    }

    /**
     * Persists an account imported from Nextcloud Files SSO.
     * Network for this account must use [NextcloudDavAccessFactory] (SSO bridge), not Basic Auth.
     */
    fun importSsoAccount(
        serverUrl: String,
        userId: String,
        ssoAccountName: String,
    ) {
        val base = serverUrl.trim().trimEnd('/')
        val uid = userId.trim()
        val name = ssoAccountName.trim()
        if (base.isBlank() || uid.isBlank() || name.isBlank()) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_sso_import_failed)) }
            return
        }
        val normalized = if (base.startsWith("https://", ignoreCase = true) ||
            base.startsWith("http://", ignoreCase = true)
        ) {
            base
        } else {
            "https://$base"
        }
        if (!normalized.startsWith("https://", ignoreCase = true)) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_https_required)) }
            return
        }
        val folder = _ui.value.remoteFolder.ifBlank { "Photos" }
        val acc = NextcloudAccount(
            serverBaseUrl = normalized,
            userId = uid,
            loginName = uid,
            appPassword = "",
            remoteFolder = folder,
            ssoAccountName = name,
        )
        accounts.upsert(acc)
        accounts.setActiveAccountId(acc.id)
        WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
        _ui.update {
            it.copy(
                serverUrl = acc.serverBaseUrl,
                username = acc.userId,
                loginName = acc.loginName,
                password = "",
                remoteFolder = acc.remoteFolder,
                hasActiveAccount = true,
                accountUsesSso = true,
                ssoAccountName = name,
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                statusMessage = appStr(R.string.msg_sso_import_saved),
                instanceThemingPrimaryHex = carousel.getThemingPrimaryHex(acc.id),
                instanceThemingOnPrimaryHex = carousel.getThemingOnPrimaryHex(acc.id),
            )
        }
        scheduleThemingRefresh()
        loadCachedListIfAny()
    }

    fun onSsoImportCancelled() {
        _ui.update { it.copy(statusMessage = appStr(R.string.msg_sso_import_cancelled)) }
    }

    fun startNextcloudLoginV2() {
        val s = _ui.value
        if (s.serverUrl.isBlank()) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_fill_server_url)) }
            return
        }
        if (!s.serverUrl.trim().startsWith("https://", ignoreCase = true)) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_https_required)) }
            return
        }
        loginJob?.cancel()
        loginJob = viewModelScope.launch {
            val token = beginBusy()
            _ui.update { it.copy(statusMessage = appStr(R.string.status_login_starting_nextcloud)) }
            try {
                val flow = NextcloudLoginFlowV2(http)
                val started = flow.start(s.serverUrl)
                started.fold(
                    onSuccess = { start ->
                        _events.tryEmit(UiEvent.OpenUrl(start.loginUrl))
                        _ui.update { it.copy(statusMessage = appStr(R.string.status_complete_login_browser)) }
                        val polled = flow.pollUntilDone(
                            start.pollEndpoint,
                            start.pollToken,
                            expectedServerBaseUrl = s.serverUrl,
                        )
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
                                        hasActiveAccount = true,
                                        accountUsesSso = active?.usesSso == true,
                                        ssoAccountName = active?.ssoAccountName,
                                        accounts = accountsUi(),
                                        activeAccountId = accounts.getActiveAccountId(),
                                        serverUrl = active?.serverBaseUrl.orEmpty(),
                                        username = active?.userId.orEmpty(),
                                        loginName = active?.loginName.orEmpty(),
                                        password = active?.appPassword.orEmpty(),
                                        remoteFolder = active?.remoteFolder.orEmpty(),
                                        statusMessage = appStr(R.string.msg_login_complete_saved),
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
                                        statusMessage = appStr(
                                            R.string.msg_login_failed,
                                            e.message ?: e.javaClass.simpleName,
                                        ),
                                    )
                                }
                            },
                        )
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(
                                statusMessage = appStr(
                                    R.string.msg_login_could_not_start,
                                    e.message ?: e.javaClass.simpleName,
                                ),
                            )
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                endBusy(token)
            }
        }
    }

    fun saveCarouselOptions() {
        val s = _ui.value
        val cropChanged = carousel.wallpaperCropMode != s.wallpaperCropMode
        carousel.orderMode = s.orderMode
        carousel.wallpaperTarget = s.wallpaperTarget
        carousel.wallpaperCropMode = s.wallpaperCropMode
        carousel.maxImageSizeMb = s.maxImageSizeMb
        carousel.maxWallpaperDiskCacheMb = s.maxWallpaperDiskCacheMb
        carousel.autoWallpaperEnabled = s.autoWallpaperEnabled
        carousel.autoWallpaperUnmeteredOnly = s.autoWallpaperUnmeteredOnly
        carousel.autoIntervalMinutes = s.autoIntervalMinutes.coerceAtLeast(WallpaperWorkScheduler.MIN_INTERVAL_MINUTES)
        carousel.showStatusNotifications = s.showStatusNotifications
        carousel.notifyWallpaperApplied = s.notifyWallpaperApplied
        carousel.notifyLibraryRefreshed = s.notifyLibraryRefreshed
        carousel.notifyWallpaperIncludeLocation = s.notifyWallpaperIncludeLocation
        carousel.geocoderNominatimEnabled = s.geocoderNominatimEnabled
        carousel.geocoderPlatformEnabled = s.geocoderPlatformEnabled
        carousel.geocoderPhotonEnabled = s.geocoderPhotonEnabled
        carousel.geocoderOrderMode = s.geocoderOrderMode
        if (cropChanged) {
            // Force rebuild of the current wallpaper bitmap with the new fit mode.
            LastAppliedWallpaperStore.clearAppliedWallpaperIds(getApplication())
            val active = activeOrNull()
            val href = active?.let { LastAppliedWallpaperStore.getHref(getApplication(), it.id) }
            if (active != null && !href.isNullOrBlank()) {
                if (s.wallpaperTarget == WallpaperTarget.HOME_AND_LOCK) {
                    HomeWallpaperResync.schedule(getApplication(), active.id, href)
                } else {
                    viewModelScope.launch {
                        NextWallpaperApplicator.applyHref(
                            getApplication(),
                            href = href,
                            hrefsForProgress = _ui.value.imageHrefs,
                            wallpaperTargetOverride = s.wallpaperTarget,
                            orderModeOverride = s.orderMode,
                            advanceCarousel = false,
                        )
                    }
                }
            }
        }
        activeOrNull()?.let { a ->
            val folder = s.remoteFolder.trim().trim('/').ifBlank {
                a.remoteFolder.ifBlank { "Photos" }
            }
            accounts.upsert(a.copy(remoteFolder = folder))
            WallpaperDiskCache(getApplication(), a.id, carousel.maxWallpaperDiskCacheMb).enforceBudget()
        }
        WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
        refreshBatteryOptimizationStatus()
        viewModelScope.launch {
            val indices = if (s.imageHrefs.isEmpty()) {
                emptyMap()
            } else {
                computeCarouselIndicesForActiveAccount(s.imageHrefs, s.orderMode, s.excludedImageHrefs)
            }
            _ui.update {
                it.copy(
                    imageCarouselIndexByHref = indices,
                    statusMessage = appStr(R.string.msg_carousel_options_saved),
                )
            }
        }
    }

    fun clearWallpaperDiskCache() {
        val id = accounts.getActiveAccountId()
        if (id == null) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_no_active_account)) }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            WallpaperDiskCache.clear(getApplication(), id)
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_disk_cache_cleared)) }
            refreshWallpaperExif()
        }
    }

    fun clearStatus() = _ui.update { it.copy(statusMessage = null) }

    /**
     * Carica EXIF dell’ultimo sfondo applicato da NCarousel ([LastAppliedWallpaperStore]): cache disco o download WebDAV.
     */
    fun refreshWallpaperExif() {
        val acc = activeOrNull()
        if (acc == null) {
            _ui.update {
                it.copy(
                    lastWallpaperFileLabel = null,
                    lastWallpaperHref = null,
                    lastWallpaperFolderPath = null,
                    lastWallpaperPlaceLabel = null,
                    wallpaperExifLines = emptyList(),
                    wallpaperExifError = null,
                    wallpaperExifLoading = false,
                )
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(wallpaperExifLoading = true, wallpaperExifError = null) }
            val href = LastAppliedWallpaperStore.getHref(getApplication(), acc.id)
            if (href == null) {
                _ui.update {
                    it.copy(
                        wallpaperExifLoading = false,
                        lastWallpaperFileLabel = null,
                        lastWallpaperHref = null,
                        lastWallpaperFolderPath = null,
                        lastWallpaperPlaceLabel = null,
                        wallpaperExifLines = emptyList(),
                        wallpaperExifError = appStr(R.string.msg_exif_no_wallpaper_yet),
                    )
                }
                return@launch
            }
            val place = LastAppliedWallpaperStore.getPlaceLabel(getApplication(), acc.id)
            val fileLabel = href.substringAfterLast('/').trim().ifEmpty { href }
            val folderPath = run {
                val prefix = "/remote.php/dav/files/${acc.userId}/"
                val rel = href.substringAfter(prefix, href).trim().trimStart('/')
                val folderOnly = rel.substringBeforeLast('/', missingDelimiterValue = "").trim().trim('/')
                when {
                    folderOnly.isEmpty() -> null
                    else -> "$folderOnly/"
                }
            }
            // Publish href early so Library can highlight while EXIF bytes load.
            _ui.update {
                it.copy(
                    lastWallpaperFileLabel = fileLabel,
                    lastWallpaperHref = href,
                    lastWallpaperFolderPath = folderPath,
                    lastWallpaperPlaceLabel = place,
                )
            }
            val app = getApplication<Application>()
            val bytes = withContext(Dispatchers.IO) {
                val disk = WallpaperDiskCache(app, acc.id, carousel.maxWallpaperDiskCacheMb)
                disk.get(href) ?: run {
                    val client = NextcloudDavAccessFactory.create(app, http, acc)
                    client.downloadFile(href).getOrNull()
                }
            }
            if (bytes == null) {
                _ui.update {
                    it.copy(
                        wallpaperExifLoading = false,
                        lastWallpaperFileLabel = fileLabel,
                        lastWallpaperHref = href,
                        lastWallpaperFolderPath = folderPath,
                        lastWallpaperPlaceLabel = place,
                        wallpaperExifLines = emptyList(),
                        wallpaperExifError = appStr(R.string.msg_exif_download_failed),
                    )
                }
                return@launch
            }
            val lines = withContext(Dispatchers.Default) {
                ImageExifSummary.formatLines(app.resources, bytes)
            }
            _ui.update {
                it.copy(
                    wallpaperExifLoading = false,
                    lastWallpaperFileLabel = fileLabel,
                    lastWallpaperHref = href,
                    lastWallpaperFolderPath = folderPath,
                    lastWallpaperPlaceLabel = place,
                    wallpaperExifLines = lines,
                    wallpaperExifError = null,
                )
            }
        }
    }

    fun testConnection() {
        val s = _ui.value
        val active = activeOrNull()
        if (active == null) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val token = beginBusy()
            _ui.update { it.copy(statusMessage = null) }
            try {
                val client = webDavClientFor(active)
                val result = client.verifyReachable()
                _ui.update {
                    it.copy(
                        statusMessage = result.fold(
                            onSuccess = { appStr(R.string.msg_webdav_ok) },
                            onFailure = { e -> appStr(R.string.msg_error_colon, e.message ?: e.javaClass.simpleName) },
                        ),
                    )
                }
            } finally {
                endBusy(token)
            }
        }
    }

    /**
     * Persists [path] as the remote folder and refreshes the image list.
     * Does not re-open the folder-browser prompt (avoids confirm → empty → prompt loops).
     */
    fun applyRemoteFolderAndRefresh(path: String) {
        val normalized = path.trim().trim('/')
        if (normalized.isEmpty()) {
            _ui.update { it.copy(statusMessage = appStr(R.string.folder_picker_open_a_folder)) }
            return
        }
        updateRemoteFolder(normalized)
        activeOrNull()?.let { accounts.upsert(it.copy(remoteFolder = normalized)) }
        refreshImageList(offerBrowseIfEmpty = false)
    }

    fun refreshImageList(offerBrowseIfEmpty: Boolean = true) {
        val active = activeOrNull()
        if (active == null) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        refreshListJob?.cancel()
        refreshListJob = viewModelScope.launch {
            val token = beginBusy()
            try {
                // Read UI state inside the job so a folder pick just before refresh is not stale.
                val s = _ui.value
                val folder = s.remoteFolder.trim().trim('/').ifBlank {
                    active.remoteFolder.ifBlank { "Photos" }
                }
                val accountForSync = active.copy(remoteFolder = folder)
                accounts.upsert(accountForSync)
                val maxBytes = s.maxImageSizeMb.toLong() * 1024L * 1024L
                val result = syncRepo.syncFromServer(http, accountForSync, maxBytes)
                result.fold(
                    onSuccess = { list ->
                        val meta = syncRepo.readCachedHrefsWithMeta(active.id)
                        val fileIds = meta.mapNotNull { row -> row.fileId?.let { row.href to it } }.toMap()
                        val lastModified = meta.mapNotNull { row ->
                            parseDavLastModifiedEpochMs(row.lastModified)?.let { row.href to it }
                        }.toMap()
                        val sizes = meta.mapNotNull { row ->
                            row.contentLengthBytes?.takeIf { it > 0L }?.let { row.href to it }
                        }.toMap()
                        val fp = WallpaperOrderEngine.libraryFingerprint(list)
                        WallpaperOrderEngine(getApplication(), active.id).onLibraryFingerprintChanged(fp)
                        ImageListCache(getApplication(), active.id).write(list)
                        val excluded = ExcludedHrefStore(getApplication(), active.id).read()
                        val orderMode = _ui.value.orderMode
                        val carouselIndices = computeCarouselIndicesForActiveAccount(list, orderMode, excluded)
                        _ui.update {
                            it.copy(
                                imageHrefs = list,
                                imageFileIds = fileIds,
                                imageLastModifiedEpochMs = lastModified,
                                imageContentLengthBytes = sizes,
                                imageCarouselIndexByHref = carouselIndices,
                                excludedImageHrefs = excluded,
                                remoteFolder = folder,
                                statusMessage = appStr(R.string.msg_images_found, list.size),
                            )
                        }
                        val app = getApplication<Application>()
                        withContext(Dispatchers.IO) {
                            CarouselStatusNotifications.maybeShowListRefreshed(app, carousel, list.size)
                        }
                        if (list.isEmpty() && offerBrowseIfEmpty) {
                            _events.tryEmit(UiEvent.SuggestBrowseRemoteFolder(folder))
                        }
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        val msg = e.message ?: e.javaClass.simpleName
                        // Keep the previous list in the UI; only the status explains the failure.
                        _ui.update {
                            it.copy(
                                remoteFolder = folder,
                                statusMessage = appStr(R.string.msg_list_error, msg),
                            )
                        }
                        // Offer browse only when the *root* remote folder looks missing (404),
                        // not when a nested subfolder is inaccessible.
                        val rootMissing =
                            msg.contains("PROPFIND at \"$folder\"", ignoreCase = false) &&
                                (msg.contains("HTTP 404") || msg.contains(": HTTP 404"))
                        if (offerBrowseIfEmpty && rootMissing) {
                            _events.tryEmit(UiEvent.SuggestBrowseRemoteFolder(folder))
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                endBusy(token)
            }
        }
    }

    fun applyNextWallpaper() {
        val s = _ui.value
        if (s.imageHrefs.isEmpty()) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_refresh_list_first)) }
            return
        }
        if (activeOrNull() == null) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val token = beginBusy()
            _ui.update { it.copy(statusMessage = appStr(R.string.status_downloading)) }
            try {
                val err = withContext(Dispatchers.IO) {
                    NextWallpaperApplicator.applyNextSuspending(
                        getApplication(),
                        orderModeOverride = s.orderMode,
                        wallpaperTargetOverride = s.wallpaperTarget,
                    )
                }
                _ui.update {
                    it.copy(
                        statusMessage = when {
                            err == null -> appStr(R.string.msg_wallpaper_updated)
                            else -> err
                        },
                    )
                }
                if (err == null) {
                    refreshWallpaperExif()
                }
            } finally {
                endBusy(token)
            }
        }
    }

    fun applyWallpaperByHref(href: String, advanceCarousel: Boolean = false) {
        val s = _ui.value
        if (href.isBlank()) return
        if (s.imageHrefs.isEmpty()) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_refresh_list_first)) }
            return
        }
        if (activeOrNull() == null) {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val token = beginBusy()
            _ui.update { it.copy(statusMessage = appStr(R.string.status_downloading)) }
            try {
                val err = withContext(Dispatchers.IO) {
                    NextWallpaperApplicator.applyHref(
                        getApplication(),
                        href = href,
                        hrefsForProgress = s.imageHrefs,
                        wallpaperTargetOverride = s.wallpaperTarget,
                        orderModeOverride = s.orderMode,
                        advanceCarousel = advanceCarousel,
                    )
                }
                val indices = if (err == null && advanceCarousel) {
                    computeCarouselIndicesForActiveAccount(
                        s.imageHrefs,
                        s.orderMode,
                        s.excludedImageHrefs,
                    )
                } else {
                    null
                }
                _ui.update {
                    it.copy(
                        imageCarouselIndexByHref = indices ?: it.imageCarouselIndexByHref,
                        statusMessage = when {
                            err != null -> err
                            advanceCarousel -> appStr(R.string.msg_wallpaper_updated_advanced)
                            else -> appStr(R.string.msg_wallpaper_updated)
                        },
                    )
                }
                if (err == null) {
                    refreshWallpaperExif()
                }
            } finally {
                endBusy(token)
            }
        }
    }

    fun loadCachedListIfAny() {
        val active = activeOrNull() ?: return
        val accountId = active.id
        viewModelScope.launch {
            val orderMode = _ui.value.orderMode
            val loaded = withContext(Dispatchers.IO) {
                data class CachedLibrary(
                    val hrefs: List<String>,
                    val fileIds: Map<String, Long>,
                    val lastModified: Map<String, Long>,
                    val sizes: Map<String, Long>,
                    val carouselIndices: Map<String, Int>,
                    val excluded: Set<String>,
                )
                fun mapsFromMeta(
                    hrefs: List<String>,
                    meta: List<dev.nemeyes.ncarousel.data.db.ImageHrefWithMeta>,
                ): Triple<Map<String, Long>, Map<String, Long>, Map<String, Long>> {
                    val hrefSet = hrefs.toHashSet()
                    val fileIds = meta.mapNotNull { row ->
                        row.fileId?.takeIf { row.href in hrefSet }?.let { row.href to it }
                    }.toMap()
                    val lastModified = meta.mapNotNull { row ->
                        if (row.href !in hrefSet) return@mapNotNull null
                        parseDavLastModifiedEpochMs(row.lastModified)?.let { row.href to it }
                    }.toMap()
                    val sizes = meta.mapNotNull { row ->
                        if (row.href !in hrefSet) return@mapNotNull null
                        row.contentLengthBytes?.takeIf { it > 0L }?.let { row.href to it }
                    }.toMap()
                    return Triple(fileIds, lastModified, sizes)
                }
                val excluded = ExcludedHrefStore(getApplication(), accountId).read()
                val meta = syncRepo.readCachedHrefsWithMeta(accountId)
                val legacy = ImageListCache(getApplication(), accountId).read()
                if (legacy.isNotEmpty()) {
                    val (fileIds, lastModified, sizes) = mapsFromMeta(legacy, meta)
                    val carouselIndices = computeCarouselIndicesForActiveAccount(legacy, orderMode, excluded)
                    CachedLibrary(legacy, fileIds, lastModified, sizes, carouselIndices, excluded)
                } else {
                    val cached = meta.map { it.href }
                    if (cached.isEmpty()) return@withContext null
                    val (fileIds, lastModified, sizes) = mapsFromMeta(cached, meta)
                    val carouselIndices = computeCarouselIndicesForActiveAccount(cached, orderMode, excluded)
                    CachedLibrary(cached, fileIds, lastModified, sizes, carouselIndices, excluded)
                }
            } ?: return@launch
            if (activeOrNull()?.id != accountId) return@launch
            _ui.update {
                it.copy(
                    imageHrefs = loaded.hrefs,
                    imageFileIds = loaded.fileIds,
                    imageLastModifiedEpochMs = loaded.lastModified,
                    imageContentLengthBytes = loaded.sizes,
                    imageCarouselIndexByHref = loaded.carouselIndices,
                    excludedImageHrefs = loaded.excluded,
                )
            }
        }
    }

    /**
     * Downloads (or reuses disk cache) the image for [href] and emits [UiEvent.ShareImage]
     * with a FileProvider URI. Never shares the WebDAV URL or credentials.
     */
    fun shareLibraryImage(href: String, displayName: String) {
        val key = href.trim()
        if (key.isEmpty()) return
        val active = activeOrNull() ?: run {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val token = beginBusy()
            try {
                _ui.update { it.copy(statusMessage = appStr(R.string.status_preparing_share)) }
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val disk = WallpaperDiskCache(
                            getApplication(),
                            active.id,
                            carousel.maxWallpaperDiskCacheMb,
                        )
                        val bytes = disk.get(key) ?: run {
                            val client = webDavClientFor(active)
                            val downloaded = client.downloadFile(key).getOrThrow()
                            disk.put(key, downloaded)
                            downloaded
                        }
                        val file = ImageShareHelper.writeShareFile(
                            getApplication(),
                            displayName.ifBlank { key.substringAfterLast('/') },
                            bytes,
                        )
                        val uri = ImageShareHelper.contentUri(getApplication(), file)
                        val mime = ImageShareHelper.mimeTypeForFileName(file.name)
                        Triple(uri, mime, file.name)
                    }
                }
                result.fold(
                    onSuccess = { (uri, mime, _) ->
                        _ui.update { it.copy(statusMessage = null) }
                        _events.tryEmit(
                            UiEvent.ShareImage(
                                uri = uri,
                                mimeType = mime,
                                chooserTitle = appStr(R.string.nc_library_share_chooser),
                            ),
                        )
                    },
                    onFailure = { e ->
                        _ui.update {
                            it.copy(
                                statusMessage = appStr(
                                    R.string.msg_share_failed,
                                    e.message ?: e.javaClass.simpleName,
                                ),
                            )
                        }
                    },
                )
            } finally {
                endBusy(token)
            }
        }
    }

    fun toggleExcludedFromCarousel(href: String) {
        val key = href.trim()
        if (key.isBlank()) return
        val active = activeOrNull() ?: run {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val (excluded, indices, nowExcluded) = withContext(Dispatchers.IO) {
                val store = ExcludedHrefStore(getApplication(), active.id)
                val nowExcluded = store.toggle(key)
                val excluded = store.read()
                val indices = computeCarouselIndicesForActiveAccount(
                    _ui.value.imageHrefs,
                    _ui.value.orderMode,
                    excluded,
                )
                Triple(excluded, indices, nowExcluded)
            }
            _ui.update {
                it.copy(
                    excludedImageHrefs = excluded,
                    imageCarouselIndexByHref = indices,
                    statusMessage = appStr(
                        if (nowExcluded) R.string.msg_excluded_from_carousel else R.string.msg_included_in_carousel,
                    ),
                )
            }
        }
    }

    /** Framing bias for Cover / Center; centered when unset. Safe to call from UI. */
    fun cropOffsetFor(href: String): CropOffset {
        val active = activeOrNull() ?: return CropOffset.CENTER
        val key = href.trim()
        if (key.isEmpty()) return CropOffset.CENTER
        return CropOffsetStore(getApplication(), active.id).get(key)
    }

    /** Persists framing for [href] (clears entry when centered). Used by Library preview. */
    fun setCropOffset(href: String, offset: CropOffset) {
        val key = href.trim()
        if (key.isEmpty()) return
        val active = activeOrNull() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            CropOffsetStore(getApplication(), active.id).set(key, offset)
        }
    }

    /** Serialized excluded-href list for the active account (export). */
    fun exportExcludedHrefsText(): String? {
        val active = activeOrNull() ?: return null
        return ExcludedHrefStore.serialize(ExcludedHrefStore(getApplication(), active.id).read())
    }

    /**
     * Replaces the active account blacklist with hrefs parsed from [text].
     * @return number of hrefs imported, or failure if no account / no valid hrefs.
     */
    fun importExcludedHrefs(text: String): Result<Int> {
        val active = activeOrNull()
            ?: return Result.failure(IllegalStateException(appStr(R.string.msg_add_account)))
        val parsed = ExcludedHrefStore.parse(text)
        if (parsed.isEmpty()) {
            return Result.failure(IllegalArgumentException(appStr(R.string.msg_excluded_import_empty)))
        }
        viewModelScope.launch {
            val (excluded, indices) = withContext(Dispatchers.IO) {
                val store = ExcludedHrefStore(getApplication(), active.id)
                store.write(parsed)
                val excluded = store.read()
                val indices = computeCarouselIndicesForActiveAccount(
                    _ui.value.imageHrefs,
                    _ui.value.orderMode,
                    excluded,
                )
                excluded to indices
            }
            _ui.update {
                it.copy(
                    excludedImageHrefs = excluded,
                    imageCarouselIndexByHref = indices,
                    statusMessage = appStr(R.string.msg_excluded_imported, excluded.size),
                )
            }
        }
        return Result.success(parsed.size)
    }

    fun clearExcludedHrefs() {
        val active = activeOrNull() ?: run {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val indices = withContext(Dispatchers.IO) {
                ExcludedHrefStore(getApplication(), active.id).clear()
                computeCarouselIndicesForActiveAccount(
                    _ui.value.imageHrefs,
                    _ui.value.orderMode,
                    emptySet(),
                )
            }
            _ui.update {
                it.copy(
                    excludedImageHrefs = emptySet(),
                    imageCarouselIndexByHref = indices,
                    statusMessage = appStr(R.string.msg_excluded_cleared),
                )
            }
        }
    }

    fun setStatusMessage(message: String) {
        _ui.update { it.copy(statusMessage = message) }
    }

    /**
     * Lists immediate child folders under [relativePath] (empty = account root).
     * Uses the active account when present; otherwise form credentials (non-SSO).
     */
    fun listRemoteChildFolders(
        relativePath: String,
        onResult: (Result<List<String>>) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    davAccessForBrowse().listChildFolders(relativePath.trim().trim('/')).getOrThrow()
                }
            }
            onResult(result)
        }
    }

    /** True when browsing remote folders is possible (active account or filled Basic Auth form). */
    fun canBrowseRemoteFolders(): Boolean {
        val active = activeOrNull()
        if (active != null) return true
        val s = _ui.value
        return s.serverUrl.trim().startsWith("https://", ignoreCase = true) &&
            s.username.isNotBlank() &&
            s.password.isNotBlank()
    }

    private fun davAccessForBrowse(): NextcloudDavAccess {
        val active = activeOrNull()
        if (active != null) return webDavClientFor(active)
        val s = _ui.value
        if (!s.serverUrl.trim().startsWith("https://", ignoreCase = true) ||
            s.username.isBlank() ||
            s.password.isBlank()
        ) {
            error(appStr(R.string.msg_folder_browse_need_account))
        }
        return NextcloudWebDavClient(
            http,
            s.serverUrl.trim().trimEnd('/'),
            s.username.trim(),
            s.loginName.ifBlank { s.username }.trim(),
            s.password,
        )
    }

    private fun activeRemoteFolder(): String {
        val s = _ui.value
        val active = activeOrNull()
        return s.remoteFolder.trim().trim('/').ifBlank {
            active?.remoteFolder.orEmpty().trim().trim('/').ifBlank { "Photos" }
        }
    }

    private fun webDavClientFor(active: NextcloudAccount): NextcloudDavAccess =
        NextcloudDavAccessFactory.create(getApplication(), http, active)

    /** Uploads the local excluded list to `{remoteFolder}/.ncarousel/excluded-hrefs.txt`. */
    fun saveExcludedHrefsToNextcloud() {
        val active = activeOrNull() ?: run {
            _ui.update { it.copy(statusMessage = appStr(R.string.msg_add_account)) }
            return
        }
        viewModelScope.launch {
            val token = beginBusy()
            try {
                val folder = activeRemoteFolder()
                val text = ExcludedHrefStore.serialize(
                    ExcludedHrefStore(getApplication(), active.id).read(),
                )
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val client = webDavClientFor(active)
                        client.ensureFolder(ExcludedHrefStore.remoteDirPath(folder)).getOrThrow()
                        client.putFile(
                            ExcludedHrefStore.remoteFilePath(folder),
                            text.toByteArray(Charsets.UTF_8),
                        ).getOrThrow()
                    }
                }
                _ui.update {
                    it.copy(
                        statusMessage = result.fold(
                            onSuccess = { appStr(R.string.msg_excluded_saved_nextcloud) },
                            onFailure = { e ->
                                appStr(
                                    R.string.msg_excluded_save_nextcloud_failed,
                                    e.message ?: e.javaClass.simpleName,
                                )
                            },
                        ),
                    )
                }
            } finally {
                endBusy(token)
            }
        }
    }

    /**
     * Downloads the excluded list from Nextcloud and returns its text for the import confirm UI.
     * Does not apply the list until [importExcludedHrefs] runs.
     */
    fun fetchExcludedHrefsFromNextcloud(onResult: (Result<String>) -> Unit) {
        val active = activeOrNull() ?: run {
            onResult(Result.failure(IllegalStateException(appStr(R.string.msg_add_account))))
            return
        }
        viewModelScope.launch {
            val token = beginBusy()
            try {
                val folder = activeRemoteFolder()
                val result = withContext(Dispatchers.IO) {
                    val client = webDavClientFor(active)
                    val download = client.downloadRelativeFile(ExcludedHrefStore.remoteFilePath(folder))
                    download.fold(
                        onSuccess = { bytes ->
                            val text = bytes.toString(Charsets.UTF_8)
                            if (ExcludedHrefStore.parse(text).isEmpty()) {
                                Result.failure(IllegalArgumentException(appStr(R.string.msg_excluded_import_empty)))
                            } else {
                                Result.success(text)
                            }
                        },
                        onFailure = { e ->
                            if (e is NoSuchElementException) {
                                Result.failure(IllegalStateException(appStr(R.string.msg_excluded_nextcloud_missing)))
                            } else {
                                Result.failure(
                                    IllegalStateException(
                                        appStr(
                                            R.string.msg_excluded_load_nextcloud_failed,
                                            e.message ?: e.javaClass.simpleName,
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                }
                onResult(result)
            } finally {
                endBusy(token)
            }
        }
    }

    fun setActiveAccount(id: String) {
        loginJob?.cancel()
        clearBusyState()
        accounts.setActiveAccountId(id)
        WallpaperWorkScheduler.sync(getApplication(), ExistingWorkPolicy.REPLACE)
        val a = accounts.getActiveAccount()
        _ui.update {
            it.copy(
                activeAccountId = id,
                hasActiveAccount = a != null,
                accountUsesSso = a?.usesSso == true,
                ssoAccountName = a?.ssoAccountName,
                serverUrl = a?.serverBaseUrl.orEmpty(),
                username = a?.userId.orEmpty(),
                loginName = a?.loginName.orEmpty(),
                password = a?.appPassword.orEmpty(),
                remoteFolder = a?.remoteFolder.orEmpty(),
                imageHrefs = emptyList(),
                imageFileIds = emptyMap(),
                imageLastModifiedEpochMs = emptyMap(),
                imageContentLengthBytes = emptyMap(),
                imageCarouselIndexByHref = emptyMap(),
                excludedImageHrefs = emptySet(),
                lastWallpaperFileLabel = null,
                lastWallpaperHref = null,
                lastWallpaperFolderPath = null,
                lastWallpaperPlaceLabel = null,
                wallpaperExifLines = emptyList(),
                wallpaperExifError = null,
                statusMessage = appStr(R.string.msg_active_account_changed),
                instanceThemingPrimaryHex = a?.let { carousel.getThemingPrimaryHex(it.id) },
                instanceThemingOnPrimaryHex = a?.let { carousel.getThemingOnPrimaryHex(it.id) },
            )
        }
        loadCachedListIfAny()
        scheduleThemingRefresh()
        refreshWallpaperExif()
    }

    fun deleteAccount(id: String) {
        loginJob?.cancel()
        clearBusyState()
        carousel.clearThemingForAccount(id)
        LastAppliedWallpaperStore.clearForAccount(getApplication(), id)
        ExcludedHrefStore(getApplication(), id).clear()
        CropOffsetStore(getApplication(), id).clear()
        ImageListCache(getApplication(), id).clear()
        accounts.delete(id)
        val a = accounts.getActiveAccount()
        _ui.update {
            it.copy(
                accounts = accountsUi(),
                activeAccountId = accounts.getActiveAccountId(),
                hasActiveAccount = a != null,
                accountUsesSso = a?.usesSso == true,
                ssoAccountName = a?.ssoAccountName,
                serverUrl = a?.serverBaseUrl.orEmpty(),
                username = a?.userId.orEmpty(),
                loginName = a?.loginName.orEmpty(),
                password = a?.appPassword.orEmpty(),
                remoteFolder = a?.remoteFolder.orEmpty(),
                imageHrefs = emptyList(),
                imageFileIds = emptyMap(),
                imageLastModifiedEpochMs = emptyMap(),
                imageContentLengthBytes = emptyMap(),
                imageCarouselIndexByHref = emptyMap(),
                excludedImageHrefs = emptySet(),
                lastWallpaperFileLabel = null,
                lastWallpaperHref = null,
                lastWallpaperFolderPath = null,
                lastWallpaperPlaceLabel = null,
                wallpaperExifLines = emptyList(),
                wallpaperExifError = null,
                statusMessage = appStr(R.string.msg_account_removed),
                instanceThemingPrimaryHex = a?.let { carousel.getThemingPrimaryHex(it.id) },
                instanceThemingOnPrimaryHex = a?.let { carousel.getThemingOnPrimaryHex(it.id) },
            )
        }
        loadCachedListIfAny()
        scheduleThemingRefresh()
        refreshWallpaperExif()
    }
}
