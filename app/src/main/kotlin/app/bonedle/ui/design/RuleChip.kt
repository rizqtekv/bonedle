package app.bonedle.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType
import app.bonedle.engine.rules.RegionStatus
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Spacing
import app.bonedle.ui.theme.withScaleCap

/** The broken-rule mark sits in a small circle; it may not outgrow it. */
private const val MARK_MAX_SCALE = 1.3f

/** Board chips live inside a cell corner. */
private const val COMPACT_MAX_SCALE = 1.3f

/** Feedback state of a region, shown on its chip. */
enum class RuleChipState { Open, Satisfied, Broken }

/** The chip state for a region in this [RegionStatus]. */
fun RegionStatus.toChipState(): RuleChipState = when (this) {
    RegionStatus.OPEN -> RuleChipState.Open
    RegionStatus.SATISFIED -> RuleChipState.Satisfied
    RegionStatus.BROKEN -> RuleChipState.Broken
}

/** Short symbol for [rule]: `=`, `≠`, `12`, `<5`, `>7`. */
@Composable
@ReadOnlyComposable
fun ruleSymbol(rule: Rule): String = when (rule.type) {
    RuleType.EQUAL -> stringResource(R.string.rule_symbol_equal)
    RuleType.ALL_DIFFERENT -> stringResource(R.string.rule_symbol_all_different)
    RuleType.SUM_EQ -> rule.target.toString()
    RuleType.SUM_LT -> stringResource(R.string.rule_symbol_sum_lt, rule.target)
    RuleType.SUM_GT -> stringResource(R.string.rule_symbol_sum_gt, rule.target)
}

/** Spoken form of [rule], e.g. "sum equals 12". */
@Composable
@ReadOnlyComposable
fun ruleSpoken(rule: Rule): String = when (rule.type) {
    RuleType.EQUAL -> stringResource(R.string.rule_spoken_equal)
    RuleType.ALL_DIFFERENT -> stringResource(R.string.rule_spoken_all_different)
    RuleType.SUM_EQ -> stringResource(R.string.rule_spoken_sum_eq, rule.target)
    RuleType.SUM_LT -> stringResource(R.string.rule_spoken_sum_lt, rule.target)
    RuleType.SUM_GT -> stringResource(R.string.rule_spoken_sum_gt, rule.target)
}

/** Spoken name of region colour [slot]. */
@Composable
@ReadOnlyComposable
fun regionColorName(slot: Int): String = stringResource(
    when (slot) {
        0 -> R.string.region_color_teal
        1 -> R.string.region_color_orange
        2 -> R.string.region_color_violet
        3 -> R.string.region_color_pink
        4 -> R.string.region_color_sky
        else -> R.string.region_color_lime
    },
)

/**
 * A region's rule chip: solid region colour, the rule symbol, and the region's texture faintly
 * inside so it links to the board without relying on hue. Satisfied chips show a check; broken
 * ones an exclamation mark. Opaque by design (never glass).
 */
@Composable
fun RuleChip(
    rule: Rule,
    slot: Int,
    modifier: Modifier = Modifier,
    state: RuleChipState = RuleChipState.Open,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = BonedleTheme.colors
    val fill = colors.region(slot)
    val ink = colors.onRegion
    val stateText = when (state) {
        RuleChipState.Open -> null
        RuleChipState.Satisfied -> stringResource(R.string.rule_state_satisfied)
        RuleChipState.Broken -> stringResource(R.string.rule_state_broken)
    }
    val description = if (stateText == null) {
        stringResource(R.string.rule_chip_description, regionColorName(slot), ruleSpoken(rule))
    } else {
        stringResource(R.string.rule_chip_description_state, regionColorName(slot), ruleSpoken(rule), stateText)
    }
    // Compact chips sit inside a board cell's corner, so their text growth is capped.
    val label = if (compact) BonedleTheme.typography.caption.copy(fontWeight = BonedleTheme.typography.label.fontWeight).withScaleCap(COMPACT_MAX_SCALE) else BonedleTheme.typography.label
    val markSize = if (compact) 12.dp else 14.dp
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 20.dp else 28.dp)
            .clip(Shapes.chip)
            .background(fill)
            .drawBehind { drawRegionTexture(RegionTexture.forSlot(slot), ink.copy(alpha = 0.16f), scale = 0.8f) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) role = Role.Button
            }
            .padding(horizontal = if (compact) 6.dp else Spacing.md, vertical = if (compact) 1.dp else Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(ruleSymbol(rule), style = label, color = ink)
        when (state) {
            RuleChipState.Open -> Unit
            RuleChipState.Satisfied -> {
                Spacer(Modifier.width(Spacing.xs))
                Icon(BonedleIcons.Check, contentDescription = null, tint = ink, modifier = Modifier.size(markSize))
            }
            RuleChipState.Broken -> {
                Spacer(Modifier.width(Spacing.xs))
                Box(
                    Modifier
                        .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                        .clip(Shapes.chip)
                        .background(ink)
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.rule_symbol_broken),
                        style = BonedleTheme.typography.caption
                            .copy(fontWeight = BonedleTheme.typography.label.fontWeight)
                            .withScaleCap(MARK_MAX_SCALE),
                        color = fill,
                    )
                }
            }
        }
    }
}
