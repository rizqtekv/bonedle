package app.bonedle.ads

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import app.bonedle.BuildConfig
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.Clock
import app.bonedle.data.db.GameDao
import app.bonedle.data.settings.EntitlementRepository
import app.bonedle.di.ApplicationScope
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** What screens may ask of ads. [NoAds] in tests and previews. */
interface Ads {
    /** Rewarded placements with an ad loaded and allowed right now. */
    val readyRewarded: StateFlow<Set<AdPlacement>>

    /**
     * Shows the rewarded ad for [placement]. [onReward] runs once the player earned it; [onClosed]
     * runs when the ad is gone (or could not be shown).
     */
    fun showRewarded(activity: Activity, placement: AdPlacement, onReward: () -> Unit, onClosed: () -> Unit = {})

    /** "Next puzzle" after an Unlimited board: the interstitial first when it is due, then [onContinue]. */
    fun continueAfterUnlimited(activity: Activity, onContinue: () -> Unit)
}

/** No ads at all. */
object NoAds : Ads {
    override val readyRewarded: StateFlow<Set<AdPlacement>> = MutableStateFlow(emptySet())

    override fun showRewarded(activity: Activity, placement: AdPlacement, onReward: () -> Unit, onClosed: () -> Unit) = onClosed()

    override fun continueAfterUnlimited(activity: Activity, onContinue: () -> Unit) = onContinue()
}

val LocalAds = staticCompositionLocalOf<Ads> { NoAds }

/** When the last ad finished, for rules that keep other prompts away from ads. */
@Singleton
class AdHistory @Inject constructor() {
    private val _lastShownAt = MutableStateFlow<Long?>(null)
    val lastShownAt: StateFlow<Long?> = _lastShownAt.asStateFlow()

    fun shown(at: Long) {
        _lastShownAt.value = at
    }
}

/**
 * Google Mobile Ads behind [AdPolicy]. Nothing is initialised or requested until ads are allowed
 * (past the first session, consent gathered where required, and real ad units in release
 * builds). One ad per placement is kept loaded so showing it is instant; a failed load retries
 * with a growing pause.
 */
@Singleton
class AdsController @Inject constructor(
    @ApplicationContext private val context: Context,
    consent: ConsentManager,
    sessions: SessionTracker,
    private val entitlements: EntitlementRepository,
    private val dao: GameDao,
    private val store: DataStore<Preferences>,
    private val clock: Clock,
    private val analytics: Analytics,
    private val history: AdHistory,
    @ApplicationScope private val scope: CoroutineScope,
) : Ads {
    private val allowed = MutableStateFlow(false)
    private val rewarded = mutableMapOf<AdPlacement, RewardedAd>()
    private val loading = mutableSetOf<AdPlacement>()
    private var interstitial: InterstitialAd? = null
    private var initialized = false
    private val _readyRewarded = MutableStateFlow<Set<AdPlacement>>(emptySet())
    override val readyRewarded: StateFlow<Set<AdPlacement>> = _readyRewarded.asStateFlow()

    init {
        scope.launch {
            combine(consent.canRequestAds, sessions.firstSession) { can, first ->
                AdPolicy.adsAllowed(BuildConfig.ADS_ENABLED, can, first && !debugNow)
            }.distinctUntilChanged().collect { ok ->
                allowed.value = ok
                if (ok) withContext(Dispatchers.Main) { start() } else _readyRewarded.value = emptySet()
            }
        }
        // Plus owners never see interstitials: drop a loaded one and stop loading them.
        scope.launch {
            entitlements.plus.distinctUntilChanged().collect { plus ->
                withContext(Dispatchers.Main) {
                    if (plus) interstitial = null else if (initialized) loadInterstitial()
                }
            }
        }
    }

    private suspend fun start() {
        if (!initialized) {
            initialized = true
            // A general-audience game: ads capped at PG. Age-restricted treatment keeps its default
            // (unspecified), which is right for an app not directed at children or teens.
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder()
                    .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_PG)
                    .build(),
            )
            MobileAds.initialize(context)
        }
        loadRewarded(AdPlacement.EXTRA_HINT)
        loadRewarded(AdPlacement.STREAK_SAVER)
        if (!entitlements.plus.first()) loadInterstitial()
    }

    private fun unitFor(placement: AdPlacement): String = when (placement) {
        AdPlacement.EXTRA_HINT -> BuildConfig.AD_UNIT_REWARDED_HINT
        AdPlacement.STREAK_SAVER -> BuildConfig.AD_UNIT_REWARDED_FREEZE
        AdPlacement.BETWEEN_UNLIMITED -> BuildConfig.AD_UNIT_INTERSTITIAL
    }

    private fun loadRewarded(placement: AdPlacement, attempt: Int = 0) {
        if (!allowed.value || placement in rewarded || !loading.add(placement)) return
        RewardedAd.load(
            context,
            unitFor(placement),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    loading -= placement
                    rewarded[placement] = ad
                    publishReady()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading -= placement
                    retry(attempt) { loadRewarded(placement, attempt + 1) }
                }
            },
        )
    }

    private fun loadInterstitial(attempt: Int = 0) {
        if (!allowed.value || interstitial != null || !loading.add(AdPlacement.BETWEEN_UNLIMITED)) return
        InterstitialAd.load(
            context,
            unitFor(AdPlacement.BETWEEN_UNLIMITED),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    loading -= AdPlacement.BETWEEN_UNLIMITED
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading -= AdPlacement.BETWEEN_UNLIMITED
                    retry(attempt) { loadInterstitial(attempt + 1) }
                }
            },
        )
    }

    /** 30 s, 1 min, 2 min, ... up to 10 min between failed loads. */
    private fun retry(attempt: Int, load: () -> Unit) {
        scope.launch(Dispatchers.Main) {
            delay(minOf(RETRY_BASE_MS shl minOf(attempt, 5), RETRY_MAX_MS))
            load()
        }
    }

    private fun publishReady() {
        _readyRewarded.value = if (allowed.value) rewarded.keys.toSet() else emptySet()
    }

    override fun showRewarded(activity: Activity, placement: AdPlacement, onReward: () -> Unit, onClosed: () -> Unit) {
        val ad = rewarded.remove(placement)
        publishReady()
        if (ad == null || !allowed.value) {
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                analytics.log(Events.rewardedAdShown(placement.code))
            }

            override fun onAdDismissedFullScreenContent() {
                history.shown(clock.nowMillis())
                onClosed()
                loadRewarded(placement)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                loadRewarded(placement)
            }
        }
        ad.show(activity) { onReward() }
    }

    override fun continueAfterUnlimited(activity: Activity, onContinue: () -> Unit) {
        scope.launch(Dispatchers.Main) {
            val ad = interstitial
            val due = ad != null && isInterstitialDue()
            if (ad == null || !due) {
                onContinue()
                return@launch
            }
            interstitial = null
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    val now = clock.nowMillis()
                    scope.launch { store.edit { it[LAST_INTERSTITIAL] = now } }
                    analytics.log(Events.interstitialShown(AdPlacement.BETWEEN_UNLIMITED.code))
                }

                override fun onAdDismissedFullScreenContent() {
                    history.shown(clock.nowMillis())
                    onContinue()
                    loadInterstitial()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    onContinue()
                    loadInterstitial()
                }
            }
            ad.show(activity)
        }
    }

    private suspend fun isInterstitialDue(): Boolean {
        val last = store.data.map { it[LAST_INTERSTITIAL] }.first()
        return AdPolicy.interstitialDue(
            allowed = allowed.value,
            plus = entitlements.plus.first(),
            solvedSinceLast = dao.unlimitedSolvedSince(last ?: 0L),
            msSinceLast = last?.let { clock.nowMillis() - it },
        )
    }

    private companion object {
        /** Debug builds can skip the first-session rule to try ads at once. */
        val debugNow: Boolean = BuildConfig.DEBUG && BuildConfig.ADS_DEBUG_NOW
        val LAST_INTERSTITIAL = longPreferencesKey("last_interstitial_at")
        const val RETRY_BASE_MS = 30_000L
        const val RETRY_MAX_MS = 10 * 60_000L
    }
}
