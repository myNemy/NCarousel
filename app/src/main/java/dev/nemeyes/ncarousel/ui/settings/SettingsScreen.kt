package dev.nemeyes.ncarousel.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.data.GeocoderOrderMode
import dev.nemeyes.ncarousel.data.OrderMode
import dev.nemeyes.ncarousel.data.WallpaperTarget
import dev.nemeyes.ncarousel.data.CarouselStatusNotifications
import dev.nemeyes.ncarousel.data.WallpaperDiskCache
import dev.nemeyes.ncarousel.ui.components.SettingsGroup
import dev.nemeyes.ncarousel.ui.components.SettingsInlineDivider
import dev.nemeyes.ncarousel.ui.components.SettingsSwitchRow
import dev.nemeyes.ncarousel.ui.components.orderModeLabel
import dev.nemeyes.ncarousel.work.WallpaperWorkScheduler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
    onOrderModeChange: (OrderMode) -> Unit,
    onWallpaperTargetChange: (WallpaperTarget) -> Unit,
    onMaxMbChange: (String) -> Unit,
    onMaxDiskCacheMbChange: (String) -> Unit,
    onClearWallpaperDiskCache: () -> Unit,
    onAutoChange: (Boolean) -> Unit,
    onIntervalChange: (String) -> Unit,
    onShowStatusNotificationsChange: (Boolean) -> Unit,
    onNotifyWallpaperAppliedChange: (Boolean) -> Unit,
    onNotifyLibraryRefreshedChange: (Boolean) -> Unit,
    onNotifyWallpaperIncludeLocationChange: (Boolean) -> Unit,
    onGeocoderOrderModeChange: (GeocoderOrderMode) -> Unit,
    onGeocoderNominatimChange: (Boolean) -> Unit,
    onGeocoderPlatformChange: (Boolean) -> Unit,
    onGeocoderPhotonChange: (Boolean) -> Unit,
    onRequestBatteryOptimizationFromBanner: () -> Unit,
) {
    val context = LocalContext.current
    var orderExpanded by remember { mutableStateOf(false) }
    var wallpaperTargetExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var geocoderOrderExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        if (state.accounts.isNotEmpty() && state.activeAccountId != null) {
            SettingsGroup(title = stringResource(R.string.settings_group_account), icon = Icons.Outlined.ManageAccounts) {
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = it },
                ) {
                    val activeLabel = state.accounts.firstOrNull { it.id == state.activeAccountId }?.label.orEmpty()
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = !state.busy,
                            ),
                        readOnly = true,
                        value = activeLabel,
                        onValueChange = {},
                        label = { Text(stringResource(R.string.settings_active_account)) },
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
                SettingsInlineDivider()
                TextButton(
                    onClick = onDeleteActiveAccount,
                    enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.remove_active_account))
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_group_connection), icon = Icons.Outlined.Cloud) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.serverUrl,
                onValueChange = onServerChange,
                label = { Text(stringResource(R.string.field_server_url)) },
                placeholder = { Text(stringResource(R.string.placeholder_server_url)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                enabled = !state.busy,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.username,
                onValueChange = onUserChange,
                label = { Text(stringResource(R.string.field_username)) },
                singleLine = true,
                enabled = !state.busy,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.password,
                onValueChange = onPassChange,
                label = { Text(stringResource(R.string.field_password_app)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.busy,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.remoteFolder,
                onValueChange = onFolderChange,
                label = { Text(stringResource(R.string.field_remote_folder)) },
                placeholder = { Text(stringResource(R.string.placeholder_remote_folder)) },
                singleLine = true,
                enabled = !state.busy,
            )
            FilledTonalButton(
                onClick = onLoginV2,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.login_browser_recommended))
            }
            Button(
                onClick = onSaveCredentials,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_credentials))
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_group_carousel), icon = Icons.Outlined.PhotoLibrary) {
            ExposedDropdownMenuBox(
                expanded = orderExpanded,
                onExpandedChange = { orderExpanded = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = !state.busy,
                        ),
                    readOnly = true,
                    value = orderModeLabel(state.orderMode),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.field_order_mode)) },
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
            ExposedDropdownMenuBox(
                expanded = wallpaperTargetExpanded,
                onExpandedChange = { wallpaperTargetExpanded = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(
                            type = MenuAnchorType.PrimaryNotEditable,
                            enabled = !state.busy,
                        ),
                    readOnly = true,
                    value = wallpaperTargetLabelRes(state.wallpaperTarget),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.field_wallpaper_target)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = wallpaperTargetExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    enabled = !state.busy,
                )
                ExposedDropdownMenu(
                    expanded = wallpaperTargetExpanded,
                    onDismissRequest = { wallpaperTargetExpanded = false },
                ) {
                    WallpaperTarget.entries.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(wallpaperTargetLabelRes(t)) },
                            onClick = {
                                onWallpaperTargetChange(t)
                                wallpaperTargetExpanded = false
                            },
                        )
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.maxImageSizeMb.toString(),
                onValueChange = onMaxMbChange,
                label = { Text(stringResource(R.string.field_max_image_mb)) },
                supportingText = { Text(stringResource(R.string.hint_max_mb_unlimited)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.busy,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.maxWallpaperDiskCacheMb.toString(),
                onValueChange = onMaxDiskCacheMbChange,
                label = { Text(stringResource(R.string.field_disk_cache_mb)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.disk_cache_supporting,
                            WallpaperDiskCache.MIN_MB,
                            WallpaperDiskCache.MAX_MB,
                        ),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.busy,
            )
            TextButton(
                onClick = onClearWallpaperDiskCache,
                enabled = !state.busy && state.activeAccountId != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.clear_wallpaper_disk_cache))
            }
            SettingsInlineDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.auto_wallpaper_switch_title),
                checked = state.autoWallpaperEnabled,
                onCheckedChange = onAutoChange,
                enabled = !state.busy,
            )
            if (state.autoWallpaperEnabled && state.batteryOptimizationMayDelayWork) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.battery_opt_banner_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = stringResource(R.string.battery_opt_banner_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        TextButton(
                            onClick = onRequestBatteryOptimizationFromBanner,
                            enabled = !state.busy,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(stringResource(R.string.battery_opt_banner_action))
                        }
                    }
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = state.autoIntervalMinutes.toString(),
                onValueChange = onIntervalChange,
                label = { Text(stringResource(R.string.field_interval_minutes)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.interval_supporting,
                            WallpaperWorkScheduler.MIN_INTERVAL_MINUTES,
                        ),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !state.busy && state.autoWallpaperEnabled,
            )
            SettingsInlineDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.notify_switch_label),
                checked = state.showStatusNotifications,
                onCheckedChange = onShowStatusNotificationsChange,
                enabled = !state.busy,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.notify_sub_wallpaper),
                checked = state.notifyWallpaperApplied,
                onCheckedChange = onNotifyWallpaperAppliedChange,
                enabled = !state.busy && state.showStatusNotifications,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.notify_sub_list),
                checked = state.notifyLibraryRefreshed,
                onCheckedChange = onNotifyLibraryRefreshedChange,
                enabled = !state.busy && state.showStatusNotifications,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.notify_sub_place_title),
                subtitle = stringResource(R.string.notify_sub_place_subtitle),
                checked = state.notifyWallpaperIncludeLocation,
                onCheckedChange = onNotifyWallpaperIncludeLocationChange,
                enabled = !state.busy && state.showStatusNotifications && state.notifyWallpaperApplied,
            )
            if (state.showStatusNotifications && state.notifyWallpaperApplied && state.notifyWallpaperIncludeLocation) {
                Text(
                    text = stringResource(R.string.geocoder_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ExposedDropdownMenuBox(
                    expanded = geocoderOrderExpanded,
                    onExpandedChange = { geocoderOrderExpanded = it },
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(
                                type = MenuAnchorType.PrimaryNotEditable,
                                enabled = !state.busy,
                            ),
                        readOnly = true,
                        value = geocoderOrderModeLabel(state.geocoderOrderMode),
                        onValueChange = {},
                        label = { Text(stringResource(R.string.geocoder_order_label)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = geocoderOrderExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        enabled = !state.busy,
                    )
                    ExposedDropdownMenu(
                        expanded = geocoderOrderExpanded,
                        onDismissRequest = { geocoderOrderExpanded = false },
                    ) {
                        GeocoderOrderMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(geocoderOrderModeLabel(mode)) },
                                onClick = {
                                    onGeocoderOrderModeChange(mode)
                                    geocoderOrderExpanded = false
                                },
                            )
                        }
                    }
                }
                SettingsSwitchRow(
                    title = stringResource(R.string.geocoder_use_nominatim),
                    checked = state.geocoderNominatimEnabled,
                    onCheckedChange = onGeocoderNominatimChange,
                    enabled = !state.busy,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.geocoder_use_platform),
                    checked = state.geocoderPlatformEnabled,
                    onCheckedChange = onGeocoderPlatformChange,
                    enabled = !state.busy,
                )
                SettingsSwitchRow(
                    title = stringResource(R.string.geocoder_use_photon),
                    checked = state.geocoderPhotonEnabled,
                    onCheckedChange = onGeocoderPhotonChange,
                    enabled = !state.busy,
                )
            }
            TextButton(
                onClick = {
                    val app = context.applicationContext
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                        }
                    }
                    runCatching { context.startActivity(intent) }
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.notify_open_system_settings))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TextButton(
                    onClick = {
                        val app = context.applicationContext
                        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                            putExtra(Settings.EXTRA_CHANNEL_ID, CarouselStatusNotifications.CHANNEL_ID)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.notify_open_channel_settings))
                }
            }
            Button(
                onClick = onSaveCarousel,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_carousel_options_btn))
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun geocoderOrderModeLabel(mode: GeocoderOrderMode): String = when (mode) {
    GeocoderOrderMode.NOMINATIM_FIRST -> stringResource(R.string.geocoder_order_nominatim_first)
    GeocoderOrderMode.PLATFORM_FIRST -> stringResource(R.string.geocoder_order_platform_first)
    GeocoderOrderMode.PHOTON_FIRST -> stringResource(R.string.geocoder_order_photon_first)
}

@Composable
private fun wallpaperTargetLabelRes(target: WallpaperTarget): String = when (target) {
    WallpaperTarget.HOME_AND_LOCK -> stringResource(R.string.wallpaper_target_home_lock)
    WallpaperTarget.HOME_ONLY -> stringResource(R.string.wallpaper_target_home_only)
    WallpaperTarget.LOCK_ONLY -> stringResource(R.string.wallpaper_target_lock_only)
}
