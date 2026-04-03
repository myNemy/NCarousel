package dev.nemeyes.ncarousel.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.MainViewModel
import dev.nemeyes.ncarousel.UiEvent
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.OrderMode
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler

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

    LaunchedEffect(Unit) {
        viewModel.loadCachedListIfAny()
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
        AlertDialog(
            onDismissRequest = deferInitialConsent,
            title = { Text(stringResource(R.string.consent_dialog_title)) },
            text = { Text(stringResource(R.string.consent_dialog_message)) },
            confirmButton = {
                TextButton(
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
                    TextButton(onClick = deferInitialConsent) {
                        Text(stringResource(R.string.consent_later))
                    }
                }
            } else {
                null
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nextcloud → sfondo") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        HomeContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 16.dp),
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
            onTest = viewModel::testConnection,
            onRefreshList = viewModel::refreshImageList,
            onApplyNext = viewModel::applyNextWallpaper,
            onOrderModeChange = viewModel::updateOrderMode,
            onMaxMbChange = viewModel::updateMaxImageSizeMbText,
            onAutoChange = viewModel::updateAutoWallpaperEnabled,
            onIntervalChange = viewModel::updateAutoIntervalMinutesText,
            showStatusNotifications = state.showStatusNotifications,
            onShowStatusNotificationsChange = { want ->
                if (!want) {
                    viewModel.updateShowStatusNotifications(false)
                    return@HomeContent
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
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    modifier: Modifier = Modifier,
    state: MainUiState,
    onServerChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onFolderChange: (String) -> Unit,
    onSaveCredentials: () -> Unit,
    onLoginV2: () -> Unit,
    onActiveAccountChange: (String) -> Unit,
    onDeleteActiveAccount: () -> Unit,
    onSaveCarousel: () -> Unit,
    onTest: () -> Unit,
    onRefreshList: () -> Unit,
    onApplyNext: () -> Unit,
    onOrderModeChange: (OrderMode) -> Unit,
    onMaxMbChange: (String) -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onIntervalChange: (String) -> Unit,
    showStatusNotifications: Boolean,
    onShowStatusNotificationsChange: (Boolean) -> Unit,
) {
    var orderExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "WebDAV Nextcloud + WallpaperManager. Cambio automatico: catena di job WorkManager (intervallo minimo ${WallpaperWorkScheduler.MIN_INTERVAL_MINUTES} min; in Doze i ritardi possono allungarsi).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(4.dp))

        if (state.accounts.isNotEmpty() && state.activeAccountId != null) {
            Text(
                text = "Account",
                style = MaterialTheme.typography.titleMedium,
            )

            ExposedDropdownMenuBox(
                expanded = accountExpanded,
                onExpandedChange = { accountExpanded = it },
            ) {
                val activeLabel = state.accounts.firstOrNull { it.id == state.activeAccountId }?.label.orEmpty()
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    readOnly = true,
                    value = activeLabel,
                    onValueChange = {},
                    label = { Text("Account attivo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    enabled = !state.busy,
                )
                ExposedDropdownMenu(
                    expanded = accountExpanded,
                    onDismissRequest = { accountExpanded = false },
                ) {
                    state.accounts.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a.label) },
                            onClick = {
                                onActiveAccountChange(a.id)
                                accountExpanded = false
                            },
                        )
                    }
                }
            }

            TextButton(onClick = onDeleteActiveAccount, enabled = !state.busy) {
                Text("Rimuovi account attivo")
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.serverUrl,
            onValueChange = onServerChange,
            label = { Text("URL Nextcloud (senza slash finale)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            enabled = !state.busy,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.username,
            onValueChange = onUserChange,
            label = { Text("Nome utente") },
            singleLine = true,
            enabled = !state.busy,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.password,
            onValueChange = onPassChange,
            label = { Text("Password / app password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            enabled = !state.busy,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.remoteFolder,
            onValueChange = onFolderChange,
            label = { Text("Cartella remota (es. Photos)") },
            singleLine = true,
            enabled = !state.busy,
        )

        TextButton(onClick = onSaveCredentials, enabled = !state.busy) {
            Text("Salva credenziali")
        }
        TextButton(onClick = onLoginV2, enabled = !state.busy) {
            Text("Login Nextcloud (browser, consigliato)")
        }

        Text(
            text = "Carosello",
            style = MaterialTheme.typography.titleMedium,
        )

        ExposedDropdownMenuBox(
            expanded = orderExpanded,
            onExpandedChange = { orderExpanded = it },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                readOnly = true,
                value = orderModeLabel(state.orderMode),
                onValueChange = {},
                label = { Text("Modalità ordine") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = orderExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                enabled = !state.busy,
            )
            ExposedDropdownMenu(
                expanded = orderExpanded,
                onDismissRequest = { orderExpanded = false },
            ) {
                OrderMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(orderModeLabel(mode)) },
                        onClick = {
                            onOrderModeChange(mode)
                            orderExpanded = false
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.maxImageSizeMb.toString(),
            onValueChange = onMaxMbChange,
            label = { Text("Max dimensione immagine (MB, 0 = illimitato)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !state.busy,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Cambio sfondo automatico",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = state.autoWallpaperEnabled,
                onCheckedChange = onAutoChange,
                enabled = !state.busy,
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.autoIntervalMinutes.toString(),
            onValueChange = onIntervalChange,
            label = { Text("Intervallo desiderato (minuti)") },
            supportingText = {
                Text("Intervallo minimo ${WallpaperWorkScheduler.MIN_INTERVAL_MINUTES} minuto/i tra un cambio e il successivo.")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !state.busy && state.autoWallpaperEnabled,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.notify_switch_label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = showStatusNotifications,
                onCheckedChange = onShowStatusNotificationsChange,
                enabled = !state.busy,
            )
        }

        TextButton(onClick = onSaveCarousel, enabled = !state.busy) {
            Text("Salva opzioni carosello e pianifica")
        }

        Button(
            onClick = onTest,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.WifiTethering, contentDescription = null)
                Text("Prova connessione WebDAV", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Button(
            onClick = onRefreshList,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Text("Aggiorna elenco immagini", modifier = Modifier.padding(start = 8.dp))
            }
        }

        Text(
            text = "Immagini trovate: ${state.imageHrefs.size}",
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = onApplyNext,
            enabled = !state.busy && state.imageHrefs.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Applica prossima immagine (ordine selezionato)")
        }

        if (state.busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun orderModeLabel(mode: OrderMode): String = when (mode) {
    OrderMode.SEQUENTIAL -> "Sequenziale"
    OrderMode.RANDOM -> "Casuale"
    OrderMode.SHUFFLE_ONCE -> "Mescola una volta"
    OrderMode.SMART_RANDOM -> "Casuale intelligente"
    OrderMode.NO_REPEAT_SHUFFLE -> "Senza ripetere (tutto il set)"
}
