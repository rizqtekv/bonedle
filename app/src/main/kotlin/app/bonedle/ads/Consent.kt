package app.bonedle.ads

import android.app.Activity
import android.content.Context
import app.bonedle.BuildConfig
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google's User Messaging Platform (spec 9): where consent is required (EEA, UK, Switzerland,
 * and US states that ask for it) the consent form appears before any ad is requested. Gathering
 * starts in the second session, never on first launch, because no ad is shown before then.
 */
@Singleton
class ConsentManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val info: ConsentInformation = UserMessagingPlatform.getConsentInformation(context)
    private val _canRequestAds = MutableStateFlow(info.canRequestAds())
    private val _privacyOptionsRequired = MutableStateFlow(privacyRequired())
    private var gathering = false

    /** Ads may be requested: consent obtained, or not needed where the player is. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    /** Settings must offer "Privacy choices" (the player can change consent). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    /** Asks Google whether consent is needed here and, if so, shows the form. Once per launch. */
    fun gather(activity: Activity) {
        if (gathering) return
        gathering = true
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply { debugSettings(activity)?.let(::setConsentDebugSettings) }
            .build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { refresh() } },
            { refresh() },
        )
    }

    /** "Privacy choices" in Settings: the form where the player reviews or changes consent. */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refresh() }
    }

    private fun refresh() {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value = privacyRequired()
    }

    private fun privacyRequired(): Boolean =
        info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Debug builds can pretend to be in the EEA (`-Pbonedle.ump.debugEea=true`) to test the form. */
    private fun debugSettings(context: Context): ConsentDebugSettings? {
        if (!BuildConfig.DEBUG || !BuildConfig.UMP_DEBUG_EEA) return null
        return ConsentDebugSettings.Builder(context)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .apply { if (BuildConfig.UMP_TEST_DEVICE.isNotEmpty()) addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE) }
            .build()
    }
}
