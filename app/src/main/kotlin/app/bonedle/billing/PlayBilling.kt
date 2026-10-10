package app.bonedle.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** The Bonedle Plus one-time product (Play Console product ID). */
const val PLUS_PRODUCT_ID = "bonedle_plus"

/** Bonedle Plus as Play offers it, with the player's local price. [handle] is Play's own object. */
class PlusOffer(val formattedPrice: String, val priceMicros: Long, val currency: String, internal val handle: Any?)

enum class PurchaseState { PURCHASED, PENDING, OTHER }

/** A one-time purchase as Play reports it. */
data class PlayPurchase(
    val products: List<String>,
    val state: PurchaseState,
    val acknowledged: Boolean,
    val token: String,
    val originalJson: String,
    val signature: String,
)

/** What Play says after the purchase sheet. */
sealed interface PurchaseEvent {
    data class Updated(val purchases: List<PlayPurchase>) : PurchaseEvent

    data object Canceled : PurchaseEvent

    data object Failed : PurchaseEvent
}

/** The slice of Play Billing the app uses, so purchase handling can be tested without Play. */
interface PlayBilling {
    val events: Flow<PurchaseEvent>

    /** Connects (or reuses the connection); false when Play is unavailable. */
    suspend fun connect(): Boolean

    suspend fun plusOffer(): PlusOffer?

    /** The player's one-time purchases, or null when Play could not answer. */
    suspend fun purchases(): List<PlayPurchase>?

    suspend fun acknowledge(token: String): Boolean

    /** Opens Play's purchase sheet; false if it could not open. */
    fun launch(activity: Activity, offer: PlusOffer): Boolean
}

/** [PlayBilling] on the Play Billing Library. */
@Singleton
class GooglePlayBilling @Inject constructor(@ApplicationContext context: Context) : PlayBilling {
    private val _events = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val events: Flow<PurchaseEvent> = _events.asSharedFlow()
    private val connecting = Mutex()

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases ->
            val event = when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> PurchaseEvent.Updated(purchases.orEmpty().map(::toPlayPurchase))
                BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseEvent.Canceled
                // Bought earlier (for example on another device): restore picks it up.
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseEvent.Updated(emptyList())
                else -> PurchaseEvent.Failed
            }
            _events.tryEmit(event)
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun connect(): Boolean = connecting.withLock {
        if (client.isReady) return@withLock true
        withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (cont.isActive) cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            }
        } ?: false
    }

    override suspend fun plusOffer(): PlusOffer? {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PLUS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        val details = result.productDetailsList?.firstOrNull { it.productId == PLUS_PRODUCT_ID } ?: return null
        val offer = details.oneTimePurchaseOfferDetails ?: return null
        return PlusOffer(offer.formattedPrice, offer.priceAmountMicros, offer.priceCurrencyCode, details)
    }

    override suspend fun purchases(): List<PlayPurchase>? {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return null
        return result.purchasesList.map(::toPlayPurchase)
    }

    override suspend fun acknowledge(token: String): Boolean {
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        return client.acknowledgePurchase(params).responseCode == BillingClient.BillingResponseCode.OK
    }

    override fun launch(activity: Activity, offer: PlusOffer): Boolean {
        val details = offer.handle as? ProductDetails ?: return false
        val product = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { details.oneTimePurchaseOfferDetails?.offerToken?.let(::setOfferToken) }
            .build()
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(product)).build()
        return client.launchBillingFlow(activity, params).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun toPlayPurchase(p: Purchase) = PlayPurchase(
        products = p.products,
        state = when (p.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.OTHER
        },
        acknowledged = p.isAcknowledged,
        token = p.purchaseToken,
        originalJson = p.originalJson,
        signature = p.signature,
    )

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
