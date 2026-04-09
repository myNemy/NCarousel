package dev.nemeyes.ncarousel.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.R

private enum class LibrarySortMode { NAME, FOLDERS }

private data class LibraryRow(
    val href: String,
    val folderPath: String,
    val fileName: String,
)

private fun normalizeFolder(s: String): String =
    s.trim().trim('/').takeIf { it.isNotBlank() } ?: ""

private fun relativePathFromHref(href: String, remoteFolder: String): String {
    val rf = normalizeFolder(remoteFolder)
    if (rf.isEmpty()) return href.trim().trimStart('/')
    val needle = "/$rf/"
    val i = href.indexOf(needle)
    return if (i >= 0) {
        href.substring(i + needle.length)
    } else {
        href.trim().trimStart('/')
    }
}

private fun toLibraryRow(href: String, remoteFolder: String): LibraryRow {
    val rel = relativePathFromHref(href, remoteFolder).trim().trimStart('/')
    val cleanRel = rel.ifBlank { href.trim().trimStart('/') }
    val slash = cleanRel.lastIndexOf('/')
    val folder = if (slash >= 0) cleanRel.substring(0, slash) else ""
    val file = if (slash >= 0) cleanRel.substring(slash + 1) else cleanRel
    return LibraryRow(href = href, folderPath = folder, fileName = file)
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    state: MainUiState,
    onRefreshList: () -> Unit,
    onApplyHref: (String) -> Unit,
) {
    var sortMode by remember { mutableStateOf(LibrarySortMode.FOLDERS) }

    if (state.imageHrefs.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.nc_library_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.nc_library_empty_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onRefreshList, enabled = !state.busy) {
                Text(stringResource(R.string.nc_library_refresh))
            }
        }
        return
    }

    val rows = remember(state.imageHrefs, state.remoteFolder) {
        state.imageHrefs.map { toLibraryRow(it, state.remoteFolder) }
    }
    val sortedRows = remember(rows, sortMode) {
        when (sortMode) {
            LibrarySortMode.NAME ->
                rows.sortedWith(compareBy<LibraryRow> { it.fileName.lowercase() }.thenBy { it.folderPath.lowercase() })
            LibrarySortMode.FOLDERS ->
                rows.sortedWith(compareBy<LibraryRow> { it.folderPath.lowercase() }.thenBy { it.fileName.lowercase() })
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.nc_library_count, state.imageHrefs.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = sortMode == LibrarySortMode.FOLDERS,
                    onClick = { sortMode = LibrarySortMode.FOLDERS },
                    label = { Text(stringResource(R.string.nc_library_sort_folders)) },
                )
                FilterChip(
                    selected = sortMode == LibrarySortMode.NAME,
                    onClick = { sortMode = LibrarySortMode.NAME },
                    label = { Text(stringResource(R.string.nc_library_sort_name)) },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        items(sortedRows, key = { it.href }) { row ->
            ListItem(
                headlineContent = {
                    Text(
                        text = row.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = row.folderPath.ifBlank { stringResource(R.string.nc_library_root_folder) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.busy) { onApplyHref(row.href) }
                        .padding(horizontal = 8.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

