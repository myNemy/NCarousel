package dev.nemeyes.ncarousel.ui.library

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    /** true = A→Z / oldest / low index; false = Z→A / newest / high index. Date defaults to newest first. */
    var sortAscending by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    /** null = all folders; "" = remote root; else relative path under remote folder. */
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var folderMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val rootFolderLabel = stringResource(R.string.nc_library_root_folder)
    val allFoldersLabel = stringResource(R.string.nc_library_folder_filter_all)

    if (state.imageHrefs.isEmpty()) {
        PullToRefreshBox(
            isRefreshing = state.busy,
            onRefresh = onRefreshList,
            modifier = modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
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

    fun selectSortMode(mode: LibrarySortMode) {
        if (sortMode == mode) {
            sortAscending = !sortAscending
        } else {
            sortMode = mode
            // Date opens newest-first; other modes open ascending.
            sortAscending = mode != LibrarySortMode.DATE
        }
    }

    val sortedRows = remember(
        rows,
        sortMode,
        sortAscending,
        state.imageLastModifiedEpochMs,
        state.imageCarouselIndexByHref,
    ) {
        val ascending = sortAscending
        val base = when (sortMode) {
            LibrarySortMode.NAME ->
                rows.sortedWith(
                    compareBy<LibraryRow> { it.fileName.lowercase() }.thenBy { it.folderPath.lowercase() },
                )
            LibrarySortMode.FOLDERS ->
                rows.sortedWith(
                    compareBy<LibraryRow> { it.folderPath.lowercase() }.thenBy { it.fileName.lowercase() },
                )
            LibrarySortMode.DATE ->
                if (ascending) {
                    rows.sortedWith(
                        compareBy<LibraryRow> { state.imageLastModifiedEpochMs[it.href] ?: Long.MAX_VALUE }
                            .thenBy { it.fileName.lowercase() },
                    )
                } else {
                    rows.sortedWith(
                        compareByDescending<LibraryRow> { state.imageLastModifiedEpochMs[it.href] ?: Long.MIN_VALUE }
                            .thenBy { it.fileName.lowercase() },
                    )
                }
            LibrarySortMode.INDEX ->
                rows.sortedWith(
                    compareBy<LibraryRow> { state.imageCarouselIndexByHref[it.href] ?: Int.MAX_VALUE }
                        .thenBy { it.fileName.lowercase() },
                )
        }
        when {
            sortMode == LibrarySortMode.DATE -> base
            ascending -> base
            else -> base.asReversed()
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

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.nc_library_count, itemCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    singleLine = true,
                    label = { Text(stringResource(R.string.nc_library_search_label)) },
                    placeholder = { Text(stringResource(R.string.nc_library_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(),
                )
                if (showFolderFilter) {
                    ExposedDropdownMenuBox(
                        expanded = folderMenuExpanded,
                        onExpandedChange = { folderMenuExpanded = it },
                        modifier = Modifier.fillMaxWidth(),
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
                            shape = RoundedCornerShape(12.dp),
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val directionIcon = if (sortAscending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward
                    FilterChip(
                        selected = sortMode == LibrarySortMode.FOLDERS,
                        onClick = { selectSortMode(LibrarySortMode.FOLDERS) },
                        label = { Text(stringResource(R.string.nc_library_sort_folders)) },
                        leadingIcon = if (sortMode == LibrarySortMode.FOLDERS) {
                            {
                                Icon(directionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            null
                        },
                    )
                    FilterChip(
                        selected = sortMode == LibrarySortMode.NAME,
                        onClick = { selectSortMode(LibrarySortMode.NAME) },
                        label = { Text(stringResource(R.string.nc_library_sort_name)) },
                        leadingIcon = if (sortMode == LibrarySortMode.NAME) {
                            {
                                Icon(directionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            null
                        },
                    )
                    FilterChip(
                        selected = sortMode == LibrarySortMode.DATE,
                        onClick = { selectSortMode(LibrarySortMode.DATE) },
                        label = { Text(stringResource(R.string.nc_library_sort_date)) },
                        leadingIcon = if (sortMode == LibrarySortMode.DATE) {
                            {
                                Icon(directionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            null
                        },
                    )
                    FilterChip(
                        selected = sortMode == LibrarySortMode.INDEX,
                        onClick = { selectSortMode(LibrarySortMode.INDEX) },
                        label = { Text(stringResource(R.string.nc_library_sort_index)) },
                        leadingIcon = if (sortMode == LibrarySortMode.INDEX) {
                            {
                                Icon(directionIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = state.busy,
            onRefresh = onRefreshList,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                ) {
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
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            if (showFastScroll && (fastScrollVisible || dragActive)) {
                FastScroller(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .padding(end = 6.dp, top = 12.dp, bottom = 12.dp)
                            .align(Alignment.CenterEnd),
                    progress01 = progress01,
                    onJumpToProgress = { p ->
                        val i = (clamp01(p) * (itemCount - 1)).toInt().coerceIn(0, itemCount - 1)
                        scope.launch { listState.scrollToItem(i) }
                    },
                    onDragActiveChange = { active ->
                        dragActive = active
                        if (active) fastScrollVisible = true
                    },
                )
            }
            }
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
                .align(Alignment.Center),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            content = {},
        )

        val p = clamp01(progress01)
        val maxY = (maxHeight - thumbHeight).coerceAtLeast(0.dp)
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = maxY * p),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
            tonalElevation = 2.dp,
        ) {
            Box(modifier = Modifier.size(width = thumbWidth, height = thumbHeight))
        }
    }
}
