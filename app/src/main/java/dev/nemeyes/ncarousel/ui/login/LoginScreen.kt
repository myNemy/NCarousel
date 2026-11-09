package dev.nemeyes.ncarousel.ui.login

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.R
import dev.nemeyes.ncarousel.ui.components.SettingsGroup

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    state: MainUiState,
    onServerChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPassChange: (String) -> Unit,
    onCheckServer: () -> Unit,
    onBackToServer: () -> Unit,
    onToggleManualLogin: () -> Unit,
    onSaveCredentials: () -> Unit,
    onLoginV2: () -> Unit,
    onLoginSso: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Text(
                text = when (state.loginWizardStep) {
                    LoginWizardStep.SERVER -> stringResource(R.string.login_intro_step_server)
                    LoginWizardStep.SIGN_IN -> stringResource(R.string.login_intro_step_sign_in)
                },
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (state.loginWizardStep) {
            LoginWizardStep.SERVER -> {
                SettingsGroup(
                    title = stringResource(R.string.login_step_server_title),
                    icon = Icons.Outlined.Cloud,
                    collapsible = false,
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.serverUrl,
                        onValueChange = onServerChange,
                        label = { Text(stringResource(R.string.field_server_url)) },
                        placeholder = { Text(stringResource(R.string.placeholder_server_url)) },
                        supportingText = { Text(stringResource(R.string.login_server_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { onCheckServer() }),
                        enabled = !state.busy,
                    )
                    FilledTonalButton(
                        onClick = onCheckServer,
                        enabled = !state.busy && state.serverUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login_check_server))
                    }
                    OutlinedButton(
                        onClick = onLoginSso,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login_sso_nextcloud_app))
                    }
                }
            }

            LoginWizardStep.SIGN_IN -> {
                SettingsGroup(
                    title = stringResource(R.string.login_step_sign_in_title),
                    icon = Icons.Outlined.Lock,
                    collapsible = false,
                ) {
                    val probe = state.serverProbeLabel
                    Text(
                        text = if (probe.isNullOrBlank()) {
                            state.serverUrl
                        } else {
                            stringResource(R.string.login_server_confirmed, probe, state.serverUrl)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onBackToServer,
                        enabled = !state.busy,
                    ) {
                        Text(stringResource(R.string.login_change_server))
                    }
                    FilledTonalButton(
                        onClick = onLoginV2,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login_browser_recommended))
                    }
                    OutlinedButton(
                        onClick = onLoginSso,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.login_sso_nextcloud_app))
                    }
                    OutlinedButton(
                        onClick = onToggleManualLogin,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(
                                if (state.showManualLoginFields) {
                                    R.string.login_hide_manual
                                } else {
                                    R.string.login_show_manual
                                },
                            ),
                        )
                    }
                    AnimatedVisibility(visible = state.showManualLoginFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                supportingText = {
                                    Text(stringResource(R.string.login_manual_password_hint))
                                },
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
                            FilledTonalButton(
                                onClick = onSaveCredentials,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.save_credentials))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
