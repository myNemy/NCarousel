package dev.nemeyes.ncarousel.ui.library

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.R
import kotlinx.coroutines.launch
import okhttp3.Credentials

private enum class LibrarySortMode { NAME, FOLDERS, DATE, INDEX }

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

private fun previewUrl(serverBaseUrl: String, fileId: Long, sizePx: Int): String =
    Uri.parse(serverBaseUrl.trimEnd('/'))
        .buildUpon()
        .appendEncodedPath("index.php/core/preview")
        .appendQueryParameter("fileId", fileId.toString())
        .appendQueryParameter("x", sizePx.toString())
        .appendQueryParameter("y", sizePx.toString())
        .appendQueryParameter("a", "1")
        .build()
        .toString()

private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

/** Distinct folder paths under the remote folder, including ancestors; "" = files in remote root. */
private fun libraryFolderOptions(rows: List<LibraryRow>): List<String> {
    val set = linkedSetOf<String>()
    for (r in rows) {
        val p = r.folderPath
        if (p.isEmpty()) {
            set.add("")
            continue
        }
        set.add(p)
        var i = p.indexOf('/')
        while (i >= 0) {
            set.add(p.substring(0, i))
            i = p.indexOf('/', i + 1)
        }
    }
    return set.sortedWith(
        compareBy<String> { it.isNotEmpty() }.thenBy { it.lowercase() },
    )
}

/** [folderFilter] null = all; "" = remote root only; otherwise path and descendants. */
private fun matchesFolderFilter(row: LibraryRow, folderFilter: String?): Boolean {
    if (folderFilter == null) return true
    if (folderFilter.isEmpty()) return row.folderPath.isEmpty()
    return row.folderPath == folderFilter || row.folderPath.startsWith("$folderFilter/")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    state: MainUiState,
    onRefreshList: () -> Unit,
    onApplyHref: (String) -> Unit,
) {
    var sortMode by remember { mutableStateOf(LibrarySortMode.FOLDERS) }
    var query by remember { mutableStateOf("") }
    /** null = all folders; "" = remote root; else relative path under remote folder. */
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val rootFolderLabel = stringResource(R.string.nc_library_root_folder)
    val allFoldersLabel = stringResource(R.string.nc_library_folder_filter_all)

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
    val folderOptions = remember(rows) { libraryFolderOptions(rows) }
    val showFolderFilter = folderOptions.any { it.isNotEmpty() }

    LaunchedEffect(folderOptions, folderFilter) {
        if (folderFilter != null && folderFilter !in folderOptions) {
            folderFilter = null
        }
    }

    val sortedRows = remember(rows, sortMode, state.imageLastModifiedEpochMs, state.imageCarouselIndexByHref) {
        when (sortMode) {
            LibrarySortMode.NAME ->
                rows.sortedWith(
                    compareBy<LibraryRow> { it.fileName.lowercase() }.thenBy { it.folderPath.lowercase() },
                )
            LibrarySortMode.FOLDERS ->
                rows.sortedWith(
                    compareBy<LibraryRow> { it.folderPath.lowercase() }.thenBy { it.fileName.lowercase() },
                )
            LibrarySortMode.DATE ->
                rows.sortedWith(
                    compareByDescending<LibraryRow> { state.imageLastModifiedEpochMs[it.href] ?: Long.MIN_VALUE }
                        .thenBy { it.fileName.lowercase() },
                )
            LibrarySortMode.INDEX ->
                rows.sortedWith(
                    compareBy<LibraryRow> { state.imageCarouselIndexByHref[it.href] ?: Int.MAX_VALUE }
                        .thenBy { it.fileName.lowercase() },
                )
        }
    }
    val filteredRows = remember(sortedRows, query, folderFilter, state.lastWallpaperHref) {
        val q = query.trim().lowercase()
        val matched = sortedRows.filter { r ->
            matchesFolderFilter(r, folderFilter) &&
                (
                    q.isEmpty() ||
                        r.fileName.lowercase().contains(q) ||
                        r.folderPath.lowercase().contains(q)
                    )
        }
        val currentHref = state.lastWallpaperHref
        if (currentHref.isNullOrBlank()) {
            matched
        } else {
            val current = matched.filter { it.href == currentHref }
            if (current.isEmpty()) matched else current + matched.filter { it.href != currentHref }
        }
    }

    val itemCount = filteredRows.size
    val showFastScroll = itemCount >= 80
    var dragActive by remember { mutableStateOf(false) }
    var fastScrollVisible by remember { mutableStateOf(false) }
    val isListScrolling by remember { derivedStateOf { listState.isScrollInProgress } }
    val progress01 by remember(itemCount) {
        derivedStateOf {
            if (itemCount <= 1) 0f else listState.firstVisibleItemIndex.toFloat() / (itemCount - 1).toFloat()
        }
    }

    LaunchedEffect(isListScrolling) {
        if (isListScrolling) {
            fastScrollVisible = true
        } else {
            kotlinx.coroutines.delay(900)
            if (!dragActive) fastScrollVisible = false
        }
    }

    val folderFilterDisplay = when (val selected = folderFilter) {
        null -> allFoldersLabel
        "" -> rootFolderLabel
        else -> selected
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.nc_library_count, itemCount),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    enabled = !state.busy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.nc_library_search_label)) },
                    placeholder = { Text(stringResource(R.string.nc_library_search_placeholder)) },
                )
                if (showFolderFilter) {
                    Spacer(modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = folderMenuExpanded,
                        onExpandedChange = { folderMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(
                                    type = MenuAnchorType.PrimaryNotEditable,
                                    enabled = !state.busy,
                                ),
                            readOnly = true,
                            value = folderFilterDisplay,
                            onValueChange = {},
                            label = { Text(stringResource(R.string.nc_library_folder_filter_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderMenuExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            enabled = !state.busy,
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(allFoldersLabel) },
                                onClick = {
                                    folderFilter = null
                                    folderMenuExpanded = false
                                },
                            )
                            folderOptions.forEach { path ->
                                val label = path.ifBlank { rootFolderLabel }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        folderFilter = path
                                        folderMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(modifier.height(8.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
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
                    FilterChip(
                        selected = sortMode == LibrarySortMode.DATE,
                        onClick = { sortMode = LibrarySortMode.DATE },
                        label = { Text(stringResource(R.string.nc_library_sort_date)) },
                    )
                    FilterChip(
                        selected = sortMode == LibrarySortMode.INDEX,
                        onClick = { sortMode = LibrarySortMode.INDEX },
                        label = { Text(stringResource(R.string.nc_library_sort_index)) },
                    )
                }
                Spacer(modifier.height(8.dp))
            }
            itemsIndexed(filteredRows, key = { _, row -> row.href }) { idx, row ->
                val ctx = LocalContext.current
                val fileId = state.imageFileIds[row.href]
                val carouselIndex = state.imageCarouselIndexByHref[row.href] ?: (idx + 1)
                val isCurrent = state.lastWallpaperHref != null && row.href == state.lastWallpaperHref
                val rowShape = RoundedCornerShape(12.dp)
                ListItem(
                    leadingContent = {
                        if (fileId != null && state.serverUrl.isNotBlank() && state.loginName.isNotBlank() && state.password.isNotBlank()) {
                            val url = remember(state.serverUrl, fileId) { previewUrl(state.serverUrl, fileId, 192) }
                            val model = remember(url, state.loginName, state.password) {
                                ImageRequest.Builder(ctx)
                                    .data(url)
                                    .addHeader("Authorization", Credentials.basic(state.loginName, state.password))
                                    .build()
                            }
                            AsyncImage(
                                model = model,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(
                                        if (isCurrent) {
                                            Modifier
                                                .border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier.clip(RoundedCornerShape(8.dp))
                                        },
                                    ),
                            )
                        }
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.nc_library_indexed_name, carouselIndex, row.fileName),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (isCurrent) {
                                stringResource(
                                    R.string.nc_library_current_in_folder,
                                    row.folderPath.ifBlank { rootFolderLabel },
                                )
                            } else {
                                row.folderPath.ifBlank { rootFolderLabel }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .clip(rowShape)
                            .then(
                                if (isCurrent) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                        shape = rowShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = !state.busy) { onApplyHref(row.href) },
                )
            }
            item { Spacer(modifier.height(8.dp)) }
        }

        if (showFastScroll && (fastScrollVisible || dragActive)) {
            FastScroller(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .padding(end = 6.dp, top = 12.dp, bottom = 12.dp)
                        .align(androidx.compose.ui.Alignment.CenterEnd),
                progress01 = progress01,
                onJumpToProgress = { p ->
                    val i = (clamp01(p) * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
                    scope.launch { listState.scrollToItem(i + 1) } // +1 header item
                },
                onDragActiveChange = { active ->
                    dragActive = active
                    if (active) fastScrollVisible = true
                },
            )
        }
    }
}

@Composable
private fun FastScroller(
    modifier: Modifier = Modifier,
    progress01: Float,
    onJumpToProgress: (Float) -> Unit,
    onDragActiveChange: (Boolean) -> Unit,
) {
    val thumbHeight = 44.dp
    val thumbWidth = 10.dp
    val trackWidth = 3.dp

    BoxWithConstraints(
        modifier =
            modifier
                .width(18.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            onDragActiveChange(true)
                        },
                        onDragEnd = {
                            onDragActiveChange(false)
                        },
                        onDragCancel = {
                            onDragActiveChange(false)
                        },
                    ) { change, _ ->
                        val h = size.height.coerceAtLeast(1).toFloat()
                        onJumpToProgress(change.position.y / h)
                        change.consume()
                    }
                },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(trackWidth)
                .align(androidx.compose.ui.Alignment.Center),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            content = {},
        )

        val p = clamp01(progress01)
        val maxY = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        Surface(
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.TopCenter)
                .offset(y = maxY * p),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            tonalElevation = 2.dp,
        ) {
            Box(modifier = Modifier.size(width = thumbWidth, height = thumbHeight))
        }
    }
}
