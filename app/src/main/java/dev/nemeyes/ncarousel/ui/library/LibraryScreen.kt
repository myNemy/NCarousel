package dev.nemeyes.ncarousel.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.MainUiState
import dev.nemeyes.ncarousel.R

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    state: MainUiState,
    onRefreshList: () -> Unit,
    onApplyHref: (String) -> Unit,
) {
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
        }
        items(state.imageHrefs, key = { it }) { href ->
            ListItem(
                headlineContent = {
                    Text(
                        text = href,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.nc_library_tap_to_apply),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.busy) { onApplyHref(href) }
                        .padding(horizontal = 8.dp),
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

