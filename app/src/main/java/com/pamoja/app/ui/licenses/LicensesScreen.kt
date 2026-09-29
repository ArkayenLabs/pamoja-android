package com.pamoja.app.ui.licenses

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pamoja.app.R
import com.pamoja.app.data.local.licenses.OssLicense
import com.pamoja.app.ui.kit.ButtonSize
import com.pamoja.app.ui.kit.CircleIconButton
import com.pamoja.app.ui.kit.Kit
import com.pamoja.app.ui.kit.KitCard
import com.pamoja.app.ui.kit.KitScreen
import com.pamoja.app.ui.kit.KitSkeleton
import com.pamoja.app.ui.kit.ListRow
import com.pamoja.app.ui.kit.SecondaryButton
import com.pamoja.app.ui.kit.TopRow
import com.pamoja.app.ui.theme.PamojaIcons

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selected = uiState.selected

    // Back out of a licence returns to the list, which is what the gesture means
    // here. Only then does it leave the screen.
    BackHandler(enabled = selected != null) { viewModel.clearSelection() }

    LicensesContent(
        isLoading = uiState.isLoading,
        licenses = uiState.licenses,
        selected = selected,
        onSelect = viewModel::select,
        onBack = { if (selected != null) viewModel.clearSelection() else onBack() },
    )
}

/** Stateless boundary for previews and tests. */
@Composable
internal fun LicensesContent(
    isLoading: Boolean,
    licenses: List<OssLicense>,
    selected: OssLicense?,
    onSelect: (OssLicense) -> Unit,
    onBack: () -> Unit,
) {
    KitScreen(
        gutter = 20.dp,
        top = {
            TopRow(start = {
                CircleIconButton(PamojaIcons.ArrowLeft, stringResource(R.string.common_back), onClick = onBack)
            })
        },
    ) {
        Text(
            // The library's own name is the more useful heading once one is
            // open, since the reader already knows where they are.
            selected?.name ?: stringResource(R.string.licenses_title),
            style = Kit.type.headline,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            selected != null -> LicenseDetail(selected)

            isLoading -> {
                KitSkeleton(height = 20.dp)
                KitSkeleton(height = 320.dp)
            }

            licenses.isEmpty() -> KitCard(spacing = 6.dp) {
                Text(stringResource(R.string.licenses_empty_title), style = Kit.type.title)
                Text(stringResource(R.string.licenses_empty_body), style = Kit.type.body, color = Kit.colors.muted)
            }

            else -> {
                Text(
                    pluralStringResource(R.plurals.licenses_subtitle, licenses.size, licenses.size),
                    style = Kit.type.body,
                    color = Kit.colors.muted,
                )
                KitCard(spacing = 0.dp) {
                    licenses.forEachIndexed { index, license ->
                        ListRow(
                            label = license.name,
                            onClick = { onSelect(license) },
                            showDivider = index < licenses.lastIndex,
                        )
                    }
                }
            }
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
    val context = LocalContext.current
    if (license.isLink) {
        Text(stringResource(R.string.licenses_link_body), style = Kit.type.body, color = Kit.colors.muted)
        license.links.forEach { link ->
            KitCard(spacing = 10.dp) {
                Text(link, style = Kit.type.caption, color = Kit.colors.muted)
                SecondaryButton(
                    stringResource(R.string.licenses_open_link),
                    size = ButtonSize.Small,
                    onClick = {
                        // Nothing here is fatal enough to crash a legal screen
                        // over: a device with no browser simply does nothing.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
                    },
                )
            }
        }
    } else {
        KitCard {
            Text(license.body, style = Kit.type.caption)
        }
    }
}
