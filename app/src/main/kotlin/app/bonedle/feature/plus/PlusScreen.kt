package app.bonedle.feature.plus

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.bonedle.R
import app.bonedle.billing.BillingRepository
import app.bonedle.billing.PlusState
import app.bonedle.billing.RestoreResult
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.GlassTopBar
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class PlusUiState(
    val plus: PlusState = PlusState(),
    val restoring: Boolean = false,
    /** What the last "Restore purchase" found, until the next one. */
    val restoreResult: RestoreResult? = null,
)

/** The Bonedle Plus sheet: price from Play, purchase and restore. */
@HiltViewModel
class PlusViewModel @Inject constructor(private val billing: BillingRepository) : ViewModel() {
    private val restoring = MutableStateFlow(false)
    private val restoreResult = MutableStateFlow<RestoreResult?>(null)

    val state: StateFlow<PlusUiState> = combine(billing.state, restoring, restoreResult) { plus, busy, result ->
        PlusUiState(plus, busy, result)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlusUiState())

    init {
        // Loads the price if launch could not.
        viewModelScope.launch { billing.refresh() }
    }

    fun buy(activity: android.app.Activity) {
        billing.buy(activity)
    }

    fun restore() {
        viewModelScope.launch {
            restoring.value = true
            restoreResult.value = billing.refresh()
            restoring.value = false
        }
    }
}

/** What the Plus screen can do. */
data class PlusActions(val buy: () -> Unit, val restore: () -> Unit, val close: () -> Unit)

@Composable
fun PlusDestination(onClose: () -> Unit, viewModel: PlusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    PlusScreen(state, PlusActions(buy = { activity?.let(viewModel::buy) }, restore = viewModel::restore, close = onClose))
}

/**
 * Bonedle Plus (spec 5.8): a one-time purchase that removes ads, opens the full archive and
 * gives unlimited hints. The price is Play's, in the player's currency.
 */
@Composable
fun PlusScreen(state: PlusUiState, actions: PlusActions) {
    val colors = BonedleTheme.colors
    val plus = state.plus
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        GlassTopBar(
            scrolled = false,
            navigation = { BonedleIconButton(BonedleIcons.Close, stringResource(R.string.action_close), onClick = actions.close) },
        ) { }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (plus.owned) BonedleIcons.Check else BonedleIcons.Crown, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(44.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    stringResource(R.string.plus_title),
                    style = BonedleTheme.typography.display,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(if (plus.owned) R.string.plus_owned else R.string.plus_subtitle),
                    style = BonedleTheme.typography.body,
                    color = if (plus.owned) colors.success else colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            BonedleCard(Modifier.fillMaxWidth()) {
                Benefit(BonedleIcons.Shield, stringResource(R.string.plus_benefit_ads), stringResource(R.string.plus_benefit_ads_body))
                Benefit(BonedleIcons.Archive, stringResource(R.string.plus_benefit_archive), stringResource(R.string.plus_benefit_archive_body))
                Benefit(BonedleIcons.Hint, stringResource(R.string.plus_benefit_hints), stringResource(R.string.plus_benefit_hints_body))
            }
            if (!plus.owned) {
                val offer = plus.offer
                val status = when {
                    plus.pending -> stringResource(R.string.plus_pending)
                    plus.failed -> stringResource(R.string.plus_failed)
                    offer == null && plus.unavailable -> stringResource(R.string.plus_unavailable)
                    else -> null
                }
                if (status != null) {
                    Text(
                        status,
                        style = BonedleTheme.typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                BonedleButton(
                    if (offer != null) stringResource(R.string.plus_buy, offer.formattedPrice) else stringResource(R.string.plus_buy_unavailable),
                    onClick = actions.buy,
                    enabled = offer != null && !plus.pending,
                    icon = BonedleIcons.Crown,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.plus_fine_print), style = BonedleTheme.typography.caption, color = colors.textSecondary, textAlign = TextAlign.Center)
            }
            BonedleButton(
                stringResource(R.string.plus_restore),
                onClick = actions.restore,
                enabled = !state.restoring,
                style = ButtonStyle.Text,
                icon = BonedleIcons.Restore,
                modifier = Modifier.fillMaxWidth(),
            )
            state.restoreResult?.let { result ->
                Text(
                    restoreMessage(result),
                    style = BonedleTheme.typography.caption,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** The outcome of "Restore purchase", in words. */
@Composable
fun restoreMessage(result: RestoreResult): String = stringResource(
    when (result) {
        RestoreResult.RESTORED -> R.string.plus_restore_done
        RestoreResult.NOTHING_TO_RESTORE -> R.string.plus_restore_none
        RestoreResult.UNAVAILABLE -> R.string.plus_restore_unavailable
    },
)

@Composable
private fun Benefit(icon: ImageVector, title: String, body: String) {
    val colors = BonedleTheme.colors
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.padding(top = 2.dp).size(Sizes.icon))
        Spacer(Modifier.width(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(title, style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary)
            Text(body, style = BonedleTheme.typography.caption, color = colors.textSecondary)
        }
    }
}
