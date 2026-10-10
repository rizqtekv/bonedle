package app.bonedle

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.analytics.LocalAnalytics
import app.bonedle.data.Clock
import app.bonedle.data.links.InstallReferrerChecker
import app.bonedle.data.links.LinkRepository
import app.bonedle.data.links.PendingLink
import app.bonedle.data.settings.Settings
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.domain.Links
import app.bonedle.navigation.BonedleNavHost
import app.bonedle.ui.sound.LocalSoundEffects
import app.bonedle.ui.sound.SoundEffects
import app.bonedle.ui.theme.BonedleTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single activity; everything else is Compose navigation. It also receives challenge links
 * (App Links, at launch or while running) and, on the first launch after install, reads the
 * Play install referrer.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: SettingsRepository

    @Inject lateinit var clock: Clock

    @Inject lateinit var sounds: SoundEffects

    @Inject lateinit var links: LinkRepository

    @Inject lateinit var referrer: InstallReferrerChecker

    @Inject lateinit var analytics: Analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sounds.warmUp()
        // A recreated activity already handled its launch intent.
        if (savedInstanceState == null) handleLink(intent)
        lifecycleScope.launch { referrer.checkOnce() }
        setContent {
            val current: Settings? by settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val pending: PendingLink? by links.pending.collectAsStateWithLifecycle(initialValue = null)
            val loaded = current
            BonedleTheme(mode = loaded?.theme ?: Settings().theme) {
                CompositionLocalProvider(LocalSoundEffects provides sounds, LocalAnalytics provides analytics) {
                    if (loaded == null) {
                        // Settings load in a few milliseconds; until then, just the background.
                        Box(Modifier.fillMaxSize().background(BonedleTheme.colors.bgBase))
                    } else {
                        // The start destination is decided once, from the first settings read.
                        val onboarded = remember { loaded.onboarded }
                        BonedleNavHost(
                            onboarded = onboarded,
                            today = clock::today,
                            onOnboarded = { skipped ->
                                analytics.log(Events.tutorialComplete(skipped))
                                lifecycleScope.launch { settings.setOnboarded() }
                            },
                            onExit = ::finish,
                            pendingLink = pending,
                            onLinkOpened = { link -> lifecycleScope.launch { links.opened(link) } },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLink(intent)
    }

    /** A board link opens that board; any other link just opens the app. */
    private fun handleLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val link = intent.dataString?.let(Links::parse) ?: return
        lifecycleScope.launch { links.accept(link, installed = true) }
    }
}
