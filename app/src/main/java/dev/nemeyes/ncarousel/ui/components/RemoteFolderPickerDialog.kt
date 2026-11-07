package dev.nemeyes.ncarousel.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.nemeyes.ncarousel.R

/**
 * Browse Nextcloud account folders (WebDAV) and pick a relative path for the carousel remote folder.
 */
@Composable
fun RemoteFolderPickerDialog(
    initialPath: String,
    onLoadChildren: (path: String, onResult: (Result<List<String>>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onFolderSelected: (String) -> Unit,
) {
    var currentPath by remember {
        mutableStateOf(initialPath.trim().trim('/'))
    }
    var children by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload(path: String) {
        loading = true
        error = null
        onLoadChildren(path) { result ->
            loading = false
            result.fold(
                onSuccess = { children = it },
                onFailure = { e ->
                    children = emptyList()
                    error = e.message ?: e.javaClass.simpleName
                },
            )
        }
    }

    LaunchedEffect(currentPath) {
        reload(currentPath)
    }

    val displayPath = if (currentPath.isEmpty()) {
        stringResource(R.string.folder_picker_root)
    } else {
        currentPath
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        title = {
            Text(stringResource(R.string.folder_picker_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = {
                            if (currentPath.isNotEmpty()) {
                                currentPath = currentPath.substringBeforeLast('/', missingDelimiterValue = "")
                            }
                        },
                        enabled = currentPath.isNotEmpty() && !loading,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.folder_picker_up),
                        )
                    }
                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                when {
                    loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        }
                    }
                    error != null -> {
                        Text(
                            text = error.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = { reload(currentPath) }) {
                            Text(stringResource(R.string.folder_picker_retry))
                        }
                    }
                    children.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.folder_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                        ) {
                            items(children, key = { it }) { childPath ->
                                val name = childPath.substringAfterLast('/')
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !loading) {
                                            currentPath = childPath
                                        }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onFolderSelected(currentPath) },
                enabled = !loading,
            ) {
                Text(stringResource(R.string.folder_picker_use_this))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.folder_picker_cancel))
            }
        },
    )
}
