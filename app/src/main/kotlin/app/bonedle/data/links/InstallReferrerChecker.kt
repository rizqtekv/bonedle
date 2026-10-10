package app.bonedle.data.links

import android.content.Context
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.settings.GrowthPreferences
import app.bonedle.domain.Links
import app.bonedle.domain.Referrer
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Reads the Play install referrer once per install. When the install came from a challenge
 * link's web page, the board becomes the pending link, so the first launch opens it after the
 * tutorial (spec 8.2, "pass the board through the Play Install Referrer").
 */
@Singleton
class InstallReferrerChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val growth: GrowthPreferences,
    private val links: LinkRepository,
    private val analytics: Analytics,
) {
    private sealed interface Read {
        data class Done(val referrer: String?) : Read

        /** The Play Store service was unavailable; try again on the next launch. */
        data object Retry : Read
    }

    suspend fun checkOnce() {
        if (growth.referrerChecked()) return
        val read = withTimeoutOrNull(TIMEOUT_MS) { read() } ?: Read.Retry
        if (read !is Read.Done) return
        growth.setReferrerChecked()
        val path = read.referrer?.let(Referrer::boardPath) ?: return
        val link = Links.parsePath(path) ?: return
        analytics.setUserProperty(Events.USER_REFERRED, "true")
        analytics.log(Events.referredInstall(link.boardId))
        growth.setReferredBoard(link.boardId)
        links.accept(link, installed = false)
    }

    private suspend fun read(): Read = suspendCancellableCoroutine { cont ->
        val client = InstallReferrerClient.newBuilder(context).build()
        fun finish(result: Read) {
            runCatching { client.endConnection() }
            if (cont.isActive) cont.resume(result)
        }
        cont.invokeOnCancellation { runCatching { client.endConnection() } }
        val started = runCatching {
            client.startConnection(
                object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        finish(
                            when (responseCode) {
                                InstallReferrerClient.InstallReferrerResponse.OK ->
                                    runCatching { Read.Done(client.installReferrer.installReferrer) }.getOrDefault(Read.Retry)
                                // No Play Store, or one too old for the API: there will never be a referrer.
                                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                                InstallReferrerClient.InstallReferrerResponse.DEVELOPER_ERROR,
                                -> Read.Done(null)
                                else -> Read.Retry
                            },
                        )
                    }

                    override fun onInstallReferrerServiceDisconnected() = finish(Read.Retry)
                },
            )
        }
        if (started.isFailure) finish(Read.Retry)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
