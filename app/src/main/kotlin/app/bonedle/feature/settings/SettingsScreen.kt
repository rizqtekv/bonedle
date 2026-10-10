package app.bonedle.feature.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.bonedle.BuildConfig
import app.bonedle.R
import app.bonedle.data.settings.Settings
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.domain.Links
import app.bonedle.feature.play.GlassSheet
import app.bonedle.notifications.ReminderController
import app.bonedle.notifications.ReminderNotifier
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.format.formatTimeOfDay
import app.bonedle.ui.growth.openNotificationSettings
import app.bonedle.ui.growth.rememberNotificationPermission
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.GlassTopBar
import app.bonedle.ui.design.NavigationRow
import app.bonedle.ui.design.SectionHeader
import app.bonedle.ui.design.SegmentedControl
import app.bonedle.ui.design.SwitchRow
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Spacing
import app.bonedle.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val reminders: ReminderController,
) : ViewModel() {
    val settings: StateFlow<Settings> = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    fun setTheme(mode: ThemeMode) = launch { repo.setTheme(mode) }

    fun setSound(on: Boolean) = launch { repo.setSound(on) }

    fun setHaptics(on: Boolean) = launch { repo.setHaptics(on) }

    fun setHideTimer(on: Boolean) = launch { repo.setHideTimer(on) }

    fun setStrict(on: Boolean) = launch { repo.setStrictMode(on) }

    fun enableReminder() = launch { reminders.enable(settings.value.reminderMinutes) }

    fun disableReminder() = launch { reminders.disable() }

    /** The time row only shows while the reminder is on, so a new time keeps it on. */
    fun setReminderTime(minutes: Int) = launch { reminders.enable(minutes) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** What the Settings screen can change; a plain holder so the screen stays stateless. */
data class SettingsActions(
    val setTheme: (ThemeMode) -> Unit,
    val setSound: (Boolean) -> Unit,
    val setHaptics: (Boolean) -> Unit,
    val setHideTimer: (Boolean) -> Unit,
    val setStrict: (Boolean) -> Unit,
    val howToPlay: () -> Unit,
    val credits: () -> Unit,
    val back: () -> Unit,
    /** Turn the reminder on; the notification permission was already granted. */
    val enableReminder: () -> Unit = {},
    val disableReminder: () -> Unit = {},
    val setReminderTime: (Int) -> Unit = {},
)

@Composable
fun SettingsDestination(onBack: () -> Unit, onHowToPlay: () -> Unit, onCredits: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreen(
        settings,
        SettingsActions(
            viewModel::setTheme,
            viewModel::setSound,
            viewModel::setHaptics,
            viewModel::setHideTimer,
            viewModel::setStrict,
            onHowToPlay,
            onCredits,
            onBack,
            viewModel::enableReminder,
            viewModel::disableReminder,
            viewModel::setReminderTime,
        ),
    )
}

/**
 * Settings (spec 5.7). Purchases and consent preferences arrive with Bonedle Plus and ads
 * (Phase 6).
 */
@Composable
fun SettingsScreen(settings: Settings, actions: SettingsActions) {
    var pickingTime by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        SettingsContent(settings, actions, onPickTime = { pickingTime = true })
        ReminderTimeSheet(
            visible = pickingTime,
            minutes = settings.reminderMinutes,
            onSave = {
                pickingTime = false
                actions.setReminderTime(it)
            },
            onDismiss = { pickingTime = false },
        )
    }
}

@Composable
private fun SettingsContent(settings: Settings, actions: SettingsActions, onPickTime: () -> Unit) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        GlassTopBar(
            scrolled = false,
            navigation = { BonedleIconButton(BonedleIcons.Back, stringResource(R.string.action_back), onClick = actions.back) },
        ) {
            Text(stringResource(R.string.settings_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SectionHeader(stringResource(R.string.settings_section_display))
            BonedleCard(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_theme), style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary)
                SegmentedControl(
                    options = listOf(
                        stringResource(R.string.settings_theme_system),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark),
                    ),
                    selectedIndex = settings.theme.ordinal,
                    onSelect = { actions.setTheme(ThemeMode.entries[it]) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    stringResource(R.string.settings_hide_timer),
                    settings.hideTimer,
                    actions.setHideTimer,
                    body = stringResource(R.string.settings_hide_timer_body),
                    icon = BonedleIcons.EyeOff,
                )
            }
            SectionHeader(stringResource(R.string.settings_section_feel))
            BonedleCard(Modifier.fillMaxWidth()) {
                SwitchRow(stringResource(R.string.settings_sound), settings.sound, actions.setSound, icon = if (settings.sound) BonedleIcons.Sound else BonedleIcons.SoundOff)
                SwitchRow(stringResource(R.string.settings_haptics), settings.haptics, actions.setHaptics, icon = BonedleIcons.Haptics)
            }
            SectionHeader(stringResource(R.string.settings_section_play))
            BonedleCard(Modifier.fillMaxWidth()) {
                SwitchRow(stringResource(R.string.settings_strict), settings.strictMode, actions.setStrict, body = stringResource(R.string.settings_strict_body), icon = BonedleIcons.Shield)
            }
            SectionHeader(stringResource(R.string.settings_section_notifications))
            ReminderCard(settings, actions, onPickTime)
            SectionHeader(stringResource(R.string.settings_section_about))
            BonedleCard(Modifier.fillMaxWidth()) {
                NavigationRow(stringResource(R.string.settings_how_to_play), actions.howToPlay, icon = BonedleIcons.Help)
                NavigationRow(
                    stringResource(R.string.settings_privacy),
                    { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Links.PRIVACY_POLICY))) },
                    icon = BonedleIcons.Shield,
                )
                NavigationRow(stringResource(R.string.settings_credits), actions.credits, icon = BonedleIcons.Info)
            }
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.md)) {
                Text(
                    stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = BonedleTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The daily reminder (spec 8.5): the switch asks for the notification permission when needed;
 * when notifications are blocked for the app, a note links to the system settings.
 */
@Composable
private fun ReminderCard(settings: Settings, actions: SettingsActions, onPickTime: () -> Unit) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    var canPost by remember { mutableStateOf(ReminderNotifier.canPost(context)) }
    var denied by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        canPost = ReminderNotifier.canPost(context)
        if (canPost) denied = false
        onPauseOrDispose { }
    }
    val requestPermission = rememberNotificationPermission { granted ->
        canPost = ReminderNotifier.canPost(context)
        if (granted) actions.enableReminder() else denied = true
    }
    BonedleCard(Modifier.fillMaxWidth()) {
        SwitchRow(
            stringResource(R.string.settings_reminder),
            settings.reminderEnabled,
            { on -> if (on) requestPermission() else actions.disableReminder() },
            body = stringResource(R.string.settings_reminder_body),
            icon = BonedleIcons.Bell,
        )
        if (settings.reminderEnabled) {
            NavigationRow(
                stringResource(R.string.settings_reminder_time),
                onPickTime,
                icon = BonedleIcons.Timer,
                trailing = formatTimeOfDay(context, settings.reminderMinutes),
            )
        }
        if (denied || (settings.reminderEnabled && !canPost)) {
            Text(stringResource(R.string.settings_reminder_blocked), style = BonedleTheme.typography.caption, color = colors.textSecondary)
            BonedleButton(
                stringResource(R.string.settings_reminder_open_system),
                onClick = { openNotificationSettings(context) },
                style = ButtonStyle.Text,
                icon = BonedleIcons.Settings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Picks the reminder time on a glass sheet, in the device's 12- or 24-hour style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeSheet(visible: Boolean, minutes: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    GlassSheet(visible, stringResource(R.string.reminder_time_title), onDismiss = onDismiss) {
        val picker = rememberTimePickerState(minutes / 60, minutes % 60, DateFormat.is24HourFormat(context))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.surface2,
                    clockDialSelectedContentColor = colors.onAccent,
                    clockDialUnselectedContentColor = colors.textPrimary,
                    selectorColor = colors.accent,
                    containerColor = colors.surface1,
                    periodSelectorBorderColor = colors.cellBorder,
                    periodSelectorSelectedContainerColor = colors.accent,
                    periodSelectorUnselectedContainerColor = Color.Transparent,
                    periodSelectorSelectedContentColor = colors.onAccent,
                    periodSelectorUnselectedContentColor = colors.textSecondary,
                    timeSelectorSelectedContainerColor = colors.accent,
                    timeSelectorUnselectedContainerColor = colors.surface2,
                    timeSelectorSelectedContentColor = colors.onAccent,
                    timeSelectorUnselectedContentColor = colors.textPrimary,
                ),
            )
        }
        BonedleButton(
            stringResource(R.string.action_save),
            onClick = { onSave(picker.hour * 60 + picker.minute) },
            icon = BonedleIcons.Check,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
