package com.pamoja.app.ui.licenses

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.pamoja.app.R
import com.pamoja.app.data.local.licenses.OssLicense
import com.pamoja.app.ui.components.PamojaEmptyState
import com.pamoja.app.ui.components.SkeletonBlock
import com.pamoja.app.ui.theme.LocalPamojaColors
import com.pamoja.app.ui.theme.PamojaIcons
import com.pamoja.app.ui.theme.PamojaRadii
import com.pamoja.app.ui.theme.Spacing

/**
 * The open source software Pamoja is built on.
 *
 * Legally required, and drawn here rather than handed to Google's
 * `OssLicensesMenuActivity`, which would drop the reader into a plain white
 * AppCompat screen with none of this app's type, colour or dark mode. The data
 * is still Google's: the Gradle plugin generates it from the POM of every
 * dependency that actually ships.
 *
 * List and detail share one screen, since the detail needs the whole entry and
 * passing half a megabyte of licence text through a navigation argument is not
 * a thing anyone should do.
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val colors = LocalPamojaColors.current
    val uiState by viewModel.uiState.collectAsState()

    val selected = uiState.selected

    // Back out of a licence returns to the list, which is what the gesture means
    // here. Only then does it leave the screen.
    BackHandler(enabled = selected != null) { viewModel.clearSelection() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surfaceApp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Spacing.x4, vertical = Spacing.x2),
            ) {
                IconButton(
                    onClick = { if (selected != null) viewModel.clearSelection() else onBack() },
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        painter = painterResource(PamojaIcons.ArrowLeft),
                        contentDescription = stringResource(R.string.common_back),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    // The library's own name is the more useful heading once one
                    // is open, since the reader already knows where they are.
                    text = selected?.name ?: stringResource(R.string.licenses_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                selected != null -> LicenseDetail(license = selected)
                uiState.isLoading -> LicensesSkeleton()
                uiState.licenses.isEmpty() -> PamojaEmptyState(
                    icon = PamojaIcons.Info,
                    title = stringResource(R.string.licenses_empty_title),
                    body = stringResource(R.string.licenses_empty_body),
                    modifier = Modifier.padding(top = Spacing.x10),
                )

                else -> LicenseList(
                    licenses = uiState.licenses,
                    onSelect = viewModel::select,
                )
            }
        }
    }
}

@Composable
private fun LicenseList(
    licenses: List<OssLicense>,
    onSelect: (OssLicense) -> Unit,
) {
    val colors = LocalPamojaColors.current

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = pluralStringResource(
                    R.plurals.licenses_subtitle,
                    licenses.size,
                    licenses.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(
                    start = Spacing.x6, end = Spacing.x6, bottom = Spacing.x5
                ),
            )
        }

        items(licenses, key = { it.name }) { license ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(license) }
                    .padding(horizontal = Spacing.x6, vertical = Spacing.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = license.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(Spacing.x3))
                Icon(
                    painter = painterResource(PamojaIcons.ChevronRight),
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        item {
            Spacer(
                modifier = Modifier
                    .navigationBarsPadding()
                    .height(Spacing.x10)
            )
        }
    }
}

/**
 * One licence.
 *
 * Most dependencies declare only a link in their POM, so that is what there is
 * to show. Saying so plainly and offering to open it is more honest than
 * rendering a bare URL as though it were the licence text.
 */
@Composable
private fun LicenseDetail(license: OssLicense) {
    val colors = LocalPamojaColors.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.x6)
    ) {
        if (license.isLink) {
            Text(
                text = stringResource(R.string.licenses_link_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.x4))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                license.links.forEach { link ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(PamojaRadii.md))
                            .background(colors.surface1)
                            .padding(Spacing.x4),
                    ) {
                        Text(
                            text = link,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )

                        Spacer(modifier = Modifier.height(Spacing.x3))

                        Button(
                            onClick = {
                                // Nothing here is fatal enough to crash a legal
                                // screen over: a device with no browser simply
                                // does nothing.
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, link.toUri())
                                    )
                                }
                            },
                            modifier = Modifier.height(44.dp),
                            shape = RoundedCornerShape(PamojaRadii.sm),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPrimarySubtle,
                                contentColor = colors.accentPrimary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.licenses_open_link),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = license.body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        Spacer(
            modifier = Modifier
                .navigationBarsPadding()
                .height(Spacing.x10)
        )
    }
}

/** Matches the list's rhythm: a line of summary, then rows. */
@Composable
private fun LicensesSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.x6),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 14.dp)
        Spacer(modifier = Modifier.height(Spacing.x6))
        repeat(8) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.7f), height = 16.dp)
            Spacer(modifier = Modifier.height(Spacing.x5))
        }
    }
}
