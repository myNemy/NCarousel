package dev.nemeyes.ncarousel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.MainViewModel
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.UiEvent
import dev.nemeyes.ncarousel.ui.login.LoginScreen
import dev.nemeyes.ncarousel.ui.main.MainHomeScreen
import dev.nemeyes.ncarousel.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object AppDestinations {
    const val MAIN = "main"
    const val SETTINGS = "settings"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.updateShowStatusNotifications(granted)
    }

    val firstLaunchNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.updateShowStatusNotifications(granted)
        viewModel.completeInitialConsentFlow()
    }

    LaunchedEffect(state.hasActiveAccount) {
        if (state.hasActiveAccount) {
            viewModel.loadCachedListIfAny()
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(msg)
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { ev ->
            when (ev) {
                is UiEvent.OpenUrl -> {
                    val intent = CustomTabsIntent.Builder().build()
                    intent.launchUrl(context, android.net.Uri.parse(ev.url))
                }
            }
        }
    }

    val deferInitialConsent: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.updateShowStatusNotifications(false)
        }
        viewModel.completeInitialConsentFlow()
    }

    if (state.needsInitialConsent) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = deferInitialConsent,
            title = { Text(stringResource(R.string.consent_dialog_title)) },
            text = { Text(stringResource(R.string.consent_dialog_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            firstLaunchNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.completeInitialConsentFlow()
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (Build.VERSION.SDK_INT >= 33) {
                                R.string.consent_request_notifications
                            } else {
                                R.string.consent_continue
                            },
                        ),
                    )
                }
            },
            dismissButton = if (Build.VERSION.SDK_INT >= 33) {
                {
                    androidx.compose.material3.TextButton(onClick = deferInitialConsent) {
                        Text(stringResource(R.string.consent_later))
                    }
                }
            } else {
                null
            },
        )
    }

    val onNotifyChange: (Boolean) -> Unit = { want ->
        if (!want) {
            viewModel.updateShowStatusNotifications(false)
            return@HomeScreen
        }
        if (Build.VERSION.SDK_INT >= 33) {
            when (
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            ) {
                PackageManager.PERMISSION_GRANTED ->
                    viewModel.updateShowStatusNotifications(true)
                else ->
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            viewModel.updateShowStatusNotifications(true)
        }
    }

    if (!state.hasActiveAccount) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Accedi") },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            },
        ) { inner ->
            LoginScreen(
                modifier = Modifier.padding(inner),
                state = state,
                onServerChange = viewModel::updateServerUrl,
                onUserChange = viewModel::updateUsername,
                onPassChange = viewModel::updatePassword,
                onFolderChange = viewModel::updateRemoteFolder,
                onSaveCredentials = viewModel::saveCredentials,
                onLoginV2 = viewModel::startNextcloudLoginV2,
            )
        }
    } else {
        val navController = rememberNavController()
        AuthenticatedShell(
            state = state,
            snackbar = snackbar,
            navController = navController,
            viewModel = viewModel,
            onNotifyChange = onNotifyChange,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedShell(
    state: MainUiState,
    snackbar: SnackbarHostState,
    navController: NavHostController,
    viewModel: MainViewModel,
    onNotifyChange: (Boolean) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                NavigationDrawerItem(
                    label = { Text("Inizio") },
                    selected = currentRoute == AppDestinations.MAIN,
                    onClick = {
                        scope.launch { drawerState.close() }
                        if (currentRoute != AppDestinations.MAIN) {
                            navController.popBackStack(AppDestinations.MAIN, inclusive = false)
                        }
                    },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                )
                NavigationDrawerItem(
                    label = { Text("Impostazioni") },
                    selected = currentRoute == AppDestinations.SETTINGS,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate(AppDestinations.SETTINGS) {
                            launchSingleTop = true
                        }
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
        ) { scaffoldInner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldInner),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = AppDestinations.MAIN,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(AppDestinations.MAIN) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.app_name)) },
                                    navigationIcon = {
                                        IconButton(
                                            onClick = { scope.launch { drawerState.open() } },
                                        ) {
                                            Icon(
                                                Icons.Filled.Menu,
                                                contentDescription = "Menu",
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            },
                        ) { inner ->
                            MainHomeScreen(
                                modifier = Modifier.padding(inner),
                                state = state,
                                onTest = viewModel::testConnection,
                                onRefreshList = viewModel::refreshImageList,
                                onApplyNext = viewModel::applyNextWallpaper,
                            )
                        }
                    }
                    composable(AppDestinations.SETTINGS) {
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Impostazioni") },
                                    navigationIcon = {
                                        IconButton(onClick = { navController.popBackStack() }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Indietro",
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                )
                            },
                        ) { inner ->
                            SettingsScreen(
                                modifier = Modifier.padding(inner),
                                state = state,
                                onServerChange = viewModel::updateServerUrl,
                                onUserChange = viewModel::updateUsername,
                                onPassChange = viewModel::updatePassword,
                                onFolderChange = viewModel::updateRemoteFolder,
                                onSaveCredentials = viewModel::saveCredentials,
                                onLoginV2 = viewModel::startNextcloudLoginV2,
                                onActiveAccountChange = viewModel::setActiveAccount,
                                onDeleteActiveAccount = {
                                    val id = viewModel.ui.value.activeAccountId
                                    if (id != null) viewModel.deleteAccount(id)
                                },
                                onSaveCarousel = viewModel::saveCarouselOptions,
                                onOrderModeChange = viewModel::updateOrderMode,
                                onMaxMbChange = viewModel::updateMaxImageSizeMbText,
                                onMaxDiskCacheMbChange = viewModel::updateMaxWallpaperDiskCacheMbText,
                                onClearWallpaperDiskCache = viewModel::clearWallpaperDiskCache,
                                onAutoChange = viewModel::updateAutoWallpaperEnabled,
                                onIntervalChange = viewModel::updateAutoIntervalMinutesText,
                                showStatusNotifications = state.showStatusNotifications,
                                onShowStatusNotificationsChange = onNotifyChange,
                            )
                        }
                    }
                }
            }
        }
    }
}
