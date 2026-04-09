package dev.nemeyes.ncarousel.ui.library

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.R
import okhttp3.Credentials
import kotlinx.coroutines.launch

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

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    state: MainUiState,
    onRefreshList: () -> Unit,
    onApplyHref: (String) -> Unit,
) {
    var sortMode by remember { mutableStateOf(LibrarySortMode.FOLDERS) }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

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

    val itemCount = sortedRows.size
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
            // Hide shortly after scrolling stops.
            kotlinx.coroutines.delay(900)
            if (!dragActive) fastScrollVisible = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
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
                val ctx = LocalContext.current
                val fileId = state.imageFileIds[row.href]
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
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    },
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

    Box(
        modifier =
            modifier
                .width(18.dp)
                .pointerInput(Unit) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val ch = event.changes.firstOrNull() ?: continue
                        if (ch.pressed) {
                            onDragActiveChange(true)
                            val h = size.height.coerceAtLeast(1f)
                            onJumpToProgress(ch.position.y / h)
                            ch.consume()
                        } else {
                            onDragActiveChange(false)
                        }
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
        Box(modifier = Modifier.fillMaxHeight()) {
            Surface(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopCenter)
                    .offset(y = ((this@Box.maxHeight - thumbHeight) * p).coerceAtLeast(0.dp)),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                tonalElevation = 2.dp,
            ) {
                Box(modifier = Modifier.size(width = thumbWidth, height = thumbHeight))
            }
        }
    }
}

