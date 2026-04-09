package dev.nemeyes.ncarousel.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nemeyes.ncarousel.BuildConfig
import dev.nemeyes.ncarousel.R

data class LicenseLink(
    val title: String,
    val subtitle: String,
    val url: String,
)

@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit,
) {
    val repoUrl = "https://github.com/myNemy/NCarousel"
    val forgejoUrl = "https://forgejo.it/Nemeyes/NCarousel"
    val issuesUrl = "https://github.com/myNemy/NCarousel/issues"

    val licenses = listOf(
        LicenseLink(
            title = "Coil",
            subtitle = "Apache-2.0",
            url = "https://github.com/coil-kt/coil/blob/main/LICENSE.txt",
        ),
        LicenseLink(
            title = "OkHttp",
            subtitle = "Apache-2.0",
            url = "https://square.github.io/okhttp/#license",
        ),
        LicenseLink(
            title = "AndroidX / Jetpack Compose",
            subtitle = "Apache-2.0",
            url = "https://source.android.com/docs/setup/about/licenses",
        ),
        LicenseLink(
            title = "Room",
            subtitle = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/room#license",
        ),
        LicenseLink(
            title = "WorkManager",
            subtitle = "Apache-2.0",
            url = "https://developer.android.com/jetpack/androidx/releases/work#license",
        ),
    )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.nc_about_title),
            style = MaterialTheme.typography.titleLarge,
        )

        OutlinedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(
                        R.string.nc_about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.nc_about_summary),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        OutlinedCard {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.nc_about_links),
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onOpenUrl(repoUrl) }) { Text(stringResource(R.string.nc_about_github)) }
                    Button(onClick = { onOpenUrl(forgejoUrl) }) { Text(stringResource(R.string.nc_about_forgejo)) }
                }
                Button(onClick = { onOpenUrl(issuesUrl) }) { Text(stringResource(R.string.nc_about_issues)) }
            }
        }

        Text(
            text = stringResource(R.string.nc_about_oss_licenses),
            style = MaterialTheme.typography.titleMedium,
        )
        licenses.forEach { l ->
            OutlinedCard {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = l.title, style = MaterialTheme.typography.titleMedium)
                    Text(text = l.subtitle, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { onOpenUrl(l.url) }) {
                        Text(stringResource(R.string.nc_about_view_license))
                    }
                }
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }
}

