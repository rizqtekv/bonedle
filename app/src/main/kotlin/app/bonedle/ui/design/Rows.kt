package app.bonedle.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing

/** A settings row with a switch; the whole row toggles and reads as one switch to TalkBack. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    icon: ImageVector? = null,
) {
    val colors = BonedleTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = Sizes.touchTarget)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary)
            if (body != null) Text(body, style = BonedleTheme.typography.caption, color = colors.textSecondary)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surface2,
                uncheckedBorderColor = colors.cellBorder,
            ),
        )
    }
}

/** A row that opens something (a screen, a link). */
@Composable
fun NavigationRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, trailing: String? = null) {
    val colors = BonedleTheme.colors
    Row(
        modifier = modifier
            .heightIn(min = Sizes.touchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.md))
        }
        Text(title, style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = BonedleTheme.typography.caption, color = colors.textSecondary)
        Icon(BonedleIcons.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Sizes.iconSmall))
    }
}

/** A labelled number tile (Stats, Solved). Read as "label, value". */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, spokenValue: String = value, icon: ImageVector? = null) {
    val colors = BonedleTheme.colors
    BonedleCard(modifier = modifier.clearAndSetSemantics { contentDescription = "$label, $spokenValue" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(label, style = BonedleTheme.typography.caption, color = colors.textSecondary)
        }
        Text(value, style = BonedleTheme.typography.title.copy(fontFeatureSettings = "tnum"), color = colors.textPrimary)
    }
}

/** Screen title in display type, marked as a heading. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text, style = BonedleTheme.typography.display, color = BonedleTheme.colors.textPrimary, modifier = Modifier.semantics { heading() })
        if (subtitle != null) Text(subtitle, style = BonedleTheme.typography.body, color = BonedleTheme.colors.textSecondary)
    }
}

/** The Bonedle mark (the launcher domino) and wordmark. */
@Composable
fun BonedleLogo(modifier: Modifier = Modifier, markSize: Dp = 36.dp) {
    val name = stringResource(R.string.app_name)
    Row(modifier.semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(markSize * 1.6f))
        Text(name, style = BonedleTheme.typography.title, color = BonedleTheme.colors.textPrimary)
    }
}
