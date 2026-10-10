package app.bonedle.billing

import android.app.Activity
import app.bonedle.BuildConfig
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.settings.EntitlementRepository
import app.bonedle.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/** Bonedle Plus as the screens see it. */
data class PlusState(
    val owned: Boolean = false,
    /** Price and purchase handle, once Play answered. */
    val offer: PlusOffer? = null,
    /** Bought, but payment is still pending (for example cash at a store). */
    val pending: Boolean = false,
    /** Play could not be reached or does not sell Plus here. */
    val unavailable: Boolean = false,
    /** The purchase sheet just failed to open or reported an error. */
    val failed: Boolean = false,
)

/** What "Restore purchases" found. */
enum class RestoreResult { RESTORED, NOTHING_TO_RESTORE, UNAVAILABLE }

/**
 * Checks Play's signature on a purchase with the app's licensing public key (Play Console >
 * Monetization setup). Without a key every purchase Play reports is trusted.
 */
object PurchaseSignature {
    fun verify(publicKeyBase64: String, signedData: String, signature: String): Boolean {
        if (publicKeyBase64.isBlank()) return true
        if (signature.isBlank()) return false
        return runCatching {
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())))
            Signature.getInstance("SHA1withRSA").run {
                initVerify(key)
                update(signedData.toByteArray(Charsets.UTF_8))
                verify(Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)
    }
}

/**
 * Bonedle Plus (spec 9): a one-time purchase that removes interstitials, unlocks the full
 * archive and gives unlimited hints. Play is the truth; [EntitlementRepository] caches it so Plus
 * works offline. [refresh] at every launch restores the purchase after a reinstall, finishes
 * acknowledging (Play refunds unacknowledged purchases after three days) and revokes Plus after
 * a refund.
 */
@Singleton
class BillingRepository @Inject constructor(
    private val play: PlayBilling,
    private val entitlements: EntitlementRepository,
    private val analytics: Analytics,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val publicKey: String = BuildConfig.BILLING_PUBLIC_KEY
    private val _state = MutableStateFlow(PlusState())

    val state: Flow<PlusState> = combine(_state, entitlements.plus) { s, owned -> s.copy(owned = owned, pending = s.pending && !owned) }

    init {
        scope.launch { play.events.collect { onEvent(it) } }
    }

    /** Connects, applies what the player owns and loads the price. Call at launch and on "Restore". */
    suspend fun refresh(): RestoreResult {
        if (!play.connect()) {
            _state.update { it.copy(unavailable = it.offer == null) }
            return RestoreResult.UNAVAILABLE
        }
        val purchases = play.purchases()
        if (purchases != null) apply(purchases, revokeIfMissing = true)
        val offer = play.plusOffer()
        _state.update { it.copy(offer = offer ?: it.offer, unavailable = (offer ?: it.offer) == null) }
        return when {
            purchases == null -> RestoreResult.UNAVAILABLE
            entitlements.plus.first() -> RestoreResult.RESTORED
            else -> RestoreResult.NOTHING_TO_RESTORE
        }
    }

    /** Opens Play's purchase sheet for Plus. */
    fun buy(activity: Activity): Boolean {
        val offer = _state.value.offer ?: return false
        val opened = play.launch(activity, offer)
        _state.update { it.copy(failed = !opened) }
        return opened
    }

    private suspend fun onEvent(event: PurchaseEvent) {
        when (event) {
            is PurchaseEvent.Updated -> {
                val before = entitlements.plus.first()
                apply(event.purchases, revokeIfMissing = false)
                if (!before && entitlements.plus.first()) {
                    val offer = _state.value.offer
                    analytics.log(Events.purchasePlus(offer?.priceMicros ?: 0L, offer?.currency.orEmpty()))
                }
                // "Already owned" arrives with no purchases: ask Play for them.
                if (event.purchases.isEmpty()) refresh()
            }
            PurchaseEvent.Canceled -> _state.update { it.copy(failed = false) }
            PurchaseEvent.Failed -> _state.update { it.copy(failed = true) }
        }
    }

    private suspend fun apply(purchases: List<PlayPurchase>, revokeIfMissing: Boolean) {
        val plus = purchases.filter { PLUS_PRODUCT_ID in it.products && PurchaseSignature.verify(publicKey, it.originalJson, it.signature) }
        val owned = plus.filter { it.state == PurchaseState.PURCHASED }
        owned.filter { !it.acknowledged }.forEach { play.acknowledge(it.token) }
        when {
            owned.isNotEmpty() -> entitlements.setPlus(true)
            revokeIfMissing && plus.none { it.state == PurchaseState.PENDING } -> entitlements.setPlus(false)
        }
        _state.update { it.copy(pending = owned.isEmpty() && plus.any { p -> p.state == PurchaseState.PENDING }, failed = false) }
    }
}
