package app.shlinker.about

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.shlinker.BuildConfig
import app.shlinker.R
import app.shlinker.devsBuildLabel
import kotlinx.coroutines.launch

internal const val REPOSITORY_URL = "https://code.awit.at/Shlinker/Shlinker_App"
internal const val ISSUES_URL = "$REPOSITORY_URL/issues"
internal const val LICENSE_URL = "$REPOSITORY_URL/src/branch/main/LICENSE"

@Composable
fun AboutRoute(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailable = stringResource(R.string.about_open_unavailable)
    AboutScreen(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        devsBuild = devsBuildLabel() != null,
        onBack = onBack,
        onOpenLink = { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: ActivityNotFoundException) {
                scope.launch { snackbar.showSnackbar(unavailable) }
            } catch (_: IllegalArgumentException) {
                scope.launch { snackbar.showSnackbar(unavailable) }
            }
        },
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    versionName: String,
    versionCode: Int,
    devsBuild: Boolean,
    onBack: () -> Unit,
    onOpenLink: (String) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.about_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.about_version, versionName, versionCode))
                Text(stringResource(R.string.about_description))
                Text(stringResource(R.string.about_independent), style = MaterialTheme.typography.bodySmall)
            }
            if (devsBuild) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.about_devs_title)) },
                        supportingContent = { Text(stringResource(R.string.about_devs_warning)) },
                        leadingContent = { Icon(Icons.Outlined.Warning, contentDescription = null) },
                        colors = ListItemDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            headlineColor = MaterialTheme.colorScheme.onErrorContainer,
                            supportingColor = MaterialTheme.colorScheme.onErrorContainer,
                            leadingIconColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }
            Text(
                stringResource(R.string.about_links),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            AboutLink(Icons.Outlined.Code, R.string.about_source, REPOSITORY_URL, onOpenLink)
            AboutLink(Icons.Outlined.BugReport, R.string.about_issues, ISSUES_URL, onOpenLink)
            AboutLink(Icons.Outlined.Gavel, R.string.about_license, LICENSE_URL, onOpenLink)
            Text(
                stringResource(R.string.about_license_hint),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun AboutLink(icon: ImageVector, label: Int, url: String, onOpen: (String) -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        supportingContent = { Text(url) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button) { onOpen(url) },
    )
}
