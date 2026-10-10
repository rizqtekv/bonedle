package app.bonedle.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.bonedle.R
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.GlassTopBar
import app.bonedle.ui.design.SectionHeader
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Spacing

/** Bundled fonts and their licence files in assets/licenses. */
private val FONT_LICENSES = listOf("Sora" to "licenses/sora-OFL.txt", "DM Sans" to "licenses/dm-sans-OFL.txt")

/** Credits: the fonts Bonedle ships and their licences, read from the bundled files. */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        GlassTopBar(
            scrolled = false,
            navigation = { BonedleIconButton(BonedleIcons.Back, stringResource(R.string.action_back), onClick = onBack) },
        ) {
            Text(stringResource(R.string.credits_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.credits_body), style = BonedleTheme.typography.body, color = colors.textSecondary)
            SectionHeader(stringResource(R.string.credits_fonts))
            FONT_LICENSES.forEach { (name, asset) ->
                var open by rememberSaveable(name) { mutableStateOf(false) }
                val license = remember(asset) { context.assets.open(asset).bufferedReader().use { it.readText() } }
                BonedleCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.credits_font_license, name), style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary)
                    BonedleButton(
                        stringResource(if (open) R.string.credits_hide_license else R.string.credits_show_license),
                        onClick = { open = !open },
                        style = ButtonStyle.Text,
                    )
                    if (open) Text(license, style = BonedleTheme.typography.caption, color = colors.textSecondary)
                }
            }
        }
    }
}
