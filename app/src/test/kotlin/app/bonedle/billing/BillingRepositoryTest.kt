package app.bonedle.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bonedle.data.settings.EntitlementRepository
import app.bonedle.testing.RecordingAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/** Play as the tests want it to be. */
private class FakePlayBilling : PlayBilling {
    val emitted = MutableSharedFlow<PurchaseEvent>(extraBufferCapacity = 8)
    override val events = emitted
    var available = true
    var owned: List<PlayPurchase>? = emptyList()
    val acknowledged = mutableListOf<String>()
    var launched = 0

    override suspend fun connect() = available

    override suspend fun plusOffer() = if (available) PlusOffer("$3.99", 3_990_000, "USD", null) else null

    override suspend fun purchases() = if (available) owned else null

    override suspend fun acknowledge(token: String): Boolean {
        acknowledged += token
        return true
    }

    override fun launch(activity: Activity, offer: PlusOffer): Boolean {
        launched++
        return true
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class BillingRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private val play = FakePlayBilling()
    private val analytics = RecordingAnalytics()
    private lateinit var entitlements: EntitlementRepository

    @Before
    fun setUp() {
        entitlements = EntitlementRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + SupervisorJob())) { tmp.newFile("plus.preferences_pb") },
        )
    }

    /** On a plain scope over the test dispatcher, so purchase events run when the test advances. */
    private fun TestScope.repository() =
        BillingRepository(play, entitlements, analytics, CoroutineScope(dispatcher + SupervisorJob())).also { advanceUntilIdle() }

    private fun purchase(state: PurchaseState, acknowledged: Boolean = false, product: String = PLUS_PRODUCT_ID) =
        PlayPurchase(listOf(product), state, acknowledged, "token-$state", "{}", "")

    @Test
    fun buyingUnlocksPlusAcknowledgesAndLogsOnce() = runTest(dispatcher) {
        val billing = repository()
        billing.refresh()
        assertEquals("$3.99", billing.state.first().offer?.formattedPrice)
        assertTrue(billing.buy(Robolectric.buildActivity(Activity::class.java).get()))
        play.emitted.emit(PurchaseEvent.Updated(listOf(purchase(PurchaseState.PURCHASED))))
        advanceUntilIdle()
        assertTrue(billing.state.first().owned)
        assertEquals(listOf("token-PURCHASED"), play.acknowledged)
        val logged = analytics.named("purchase_plus").single().params
        assertEquals(3_990_000L, logged["price_micros"])
        assertEquals("USD", logged["currency"])
        // The same purchase reported again changes nothing.
        play.emitted.emit(PurchaseEvent.Updated(listOf(purchase(PurchaseState.PURCHASED, acknowledged = true))))
        advanceUntilIdle()
        assertEquals(1, analytics.named("purchase_plus").size)
    }

    @Test
    fun aPendingPurchaseWaitsForPayment() = runTest(dispatcher) {
        val billing = repository()
        play.emitted.emit(PurchaseEvent.Updated(listOf(purchase(PurchaseState.PENDING))))
        advanceUntilIdle()
        val state = billing.state.first()
        assertFalse(state.owned)
        assertTrue(state.pending)
        assertTrue(play.acknowledged.isEmpty())
    }

    @Test
    fun restoreBringsPlusBackAndARefundTakesItAway() = runTest(dispatcher) {
        val billing = repository()
        play.owned = listOf(purchase(PurchaseState.PURCHASED, acknowledged = true))
        assertEquals(RestoreResult.RESTORED, billing.refresh())
        assertTrue(entitlements.plus.first())
        play.owned = emptyList()
        assertEquals(RestoreResult.NOTHING_TO_RESTORE, billing.refresh())
        assertFalse(entitlements.plus.first())
    }

    @Test
    fun offlinePlayKeepsTheCachedEntitlement() = runTest(dispatcher) {
        entitlements.setPlus(true)
        val billing = repository()
        play.available = false
        assertEquals(RestoreResult.UNAVAILABLE, billing.refresh())
        assertTrue(entitlements.plus.first())
        assertTrue(billing.state.first().owned)
    }

    @Test
    fun otherProductsAreIgnored() = runTest(dispatcher) {
        val billing = repository()
        play.owned = listOf(purchase(PurchaseState.PURCHASED, product = "something_else"))
        assertEquals(RestoreResult.NOTHING_TO_RESTORE, billing.refresh())
        assertFalse(billing.state.first().owned)
    }

    @Test
    fun signaturesAreCheckedWhenAKeyIsConfigured() {
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val publicKey = Base64.getEncoder().encodeToString(keys.public.encoded)
        val json = """{"productId":"bonedle_plus","purchaseState":0}"""
        val signature = Signature.getInstance("SHA1withRSA").run {
            initSign(keys.private)
            update(json.toByteArray())
            Base64.getEncoder().encodeToString(sign())
        }
        assertTrue(PurchaseSignature.verify(publicKey, json, signature))
        assertFalse(PurchaseSignature.verify(publicKey, json.replace("0", "1"), signature))
        assertFalse(PurchaseSignature.verify(publicKey, json, ""))
        assertTrue("no key configured: trust Play", PurchaseSignature.verify("", json, ""))
    }
}
