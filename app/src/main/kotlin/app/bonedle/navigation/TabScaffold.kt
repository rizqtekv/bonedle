package app.bonedle.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.ui.design.BarItem
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.GlassNavigationBar
import app.bonedle.ui.design.ProvideGlassBackdrop
import app.bonedle.ui.design.glassBackdropSource
import app.bonedle.ui.design.rememberGlassBackdrop
import app.bonedle.ui.theme.BonedleTheme

/** The three bottom-navigation destinations. */
enum class Tab { Home, Stats, Archive }

/** Space a tab's scrolling content leaves at the bottom so the glass bar never hides it. */
val TabBarClearance = 96.dp

/**
 * Screen frame for the tabs: content scrolls under a glass bottom navigation bar, which blurs
 * it. [chrome] draws further glass (a top bar) over the same backdrop.
 */
@Composable
fun TabScaffold(
    selected: Tab,
    onTab: (Tab) -> Unit,
    chrome: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .background(BonedleTheme.colors.bgBase),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop, BonedleTheme.colors.bgBase),
            content = content,
        )
        ProvideGlassBackdrop(backdrop) {
            chrome()
            GlassNavigationBar(
                items = listOf(
                    BarItem(BonedleIcons.Home, stringResource(R.string.nav_home)),
                    BarItem(BonedleIcons.Stats, stringResource(R.string.nav_stats)),
                    BarItem(BonedleIcons.Archive, stringResource(R.string.nav_archive)),
                ),
                selectedIndex = selected.ordinal,
                onSelect = { onTab(Tab.entries[it]) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
