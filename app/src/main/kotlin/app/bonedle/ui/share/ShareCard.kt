package app.bonedle.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bonedle.engine.game.Game
import app.bonedle.engine.model.Puzzle
import app.bonedle.feature.play.BoardGeometry
import app.bonedle.feature.play.BoardMotion
import app.bonedle.feature.play.BoardView
import app.bonedle.feature.play.DragState
import app.bonedle.ui.design.BonedleLogo
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Spacing

/** Text on the share card. */
@Immutable
data class ShareCardContent(val title: String, val stats: String, val link: String, val description: String)

/** The share card's proportions: 1080 × 1350 (4:5), the Instagram portrait size. */
const val SHARE_CARD_ASPECT = 1080f / 1350f

/**
 * The spoiler-free result card (spec 8.1): logo, "Bonedle #142 · Medium", the board's regions
 * and rule chips with no dominoes, the stats line and the link. Shown as a preview on the
 * Solved screen and rendered to a 1080 × 1350 image by [ShareCardOffscreen]; the image is
 * square-cornered ([rounded] false) so chat apps do not show transparent corners.
 */
@Composable
fun ShareCard(puzzle: Puzzle, content: ShareCardContent, modifier: Modifier = Modifier, rounded: Boolean = true) {
    val colors = BonedleTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .aspectRatio(SHARE_CARD_ASPECT)
            .then(if (rounded) Modifier.clip(Shapes.card) else Modifier)
            .background(Brush.verticalGradient(listOf(colors.bgDeep, colors.bgBase)))
            .clearAndSetSemantics { contentDescription = content.description }
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BonedleLogo(markSize = 22.dp)
        Text(content.title, style = BonedleTheme.typography.heading, color = colors.textPrimary, textAlign = TextAlign.Center)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val density = LocalDensity.current
            val board = puzzle.board
            val geometry = remember(board, maxWidth, maxHeight, density) {
                with(density) {
                    val pad = 6.dp.toPx()
                    val cell = minOf((maxWidth.toPx() - pad * 2) / board.cols, (maxHeight.toPx() - pad * 2) / board.rows)
                    BoardGeometry(board, cell, pad)
                }
            }
            val empty = remember(puzzle) { Game.start(puzzle) }
            Box {
                BoardView(
                    game = empty,
                    geometry = geometry,
                    drag = remember { DragState() },
                    motion = remember { BoardMotion() },
                    highlight = emptySet(),
                    interactive = false,
                    onTapCell = {},
                    onDrop = {},
                    onChipTap = {},
                    onReturnToTray = {},
                    cellSemantics = false,
                )
            }
        }
        Text(content.stats, style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary, textAlign = TextAlign.Center)
        Text(content.link, style = BonedleTheme.typography.label, color = colors.accentText, textAlign = TextAlign.Center)
    }
}
