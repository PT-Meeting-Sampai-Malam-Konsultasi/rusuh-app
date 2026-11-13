package com.rusuh.app.analytics

import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.storage.AppPreferences

/**
 * Type-safe wrapper for analytics events. All events pass through [Analytics], respecting consent.
 *
 * The store remembers purchased order codes to prevent duplicate purchase events (Firebase
 * does not de-duplicate on Android).
 */
@Suppress("TooManyFunctions")
class AnalyticsTracker(
    private val analytics: Analytics,
    private val localeProvider: LocaleProvider,
    private val prefs: AppPreferences,
) {
    private val purchasedOrders = mutableSetOf<String>()
    private var purchasedOrdersLoaded = false

    private suspend fun ensurePurchasedOrdersLoaded() {
        if (purchasedOrdersLoaded) return
        prefs.get(KEY_PURCHASED_ORDERS)?.split(",")?.filter { it.isNotBlank() }?.let {
            purchasedOrders.addAll(it)
        }
        purchasedOrdersLoaded = true
    }

    private suspend fun markOrderPurchased(orderCode: String) {
        purchasedOrders.add(orderCode)
        prefs.set(KEY_PURCHASED_ORDERS, purchasedOrders.joinToString(","))
    }

    private fun lang(): String = localeProvider.language().tag

    fun viewItemList(items: List<ItemData>) {
        analytics.log(
            AnalyticsEvents.VIEW_ITEM_LIST,
            mapOf(
                AnalyticsEvents.ITEM_LIST_NAME to "home",
                AnalyticsEvents.ITEMS to items.map { it.toMap() },
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun selectItem(item: ItemData) {
        analytics.log(
            AnalyticsEvents.SELECT_ITEM,
            mapOf(
                AnalyticsEvents.ITEM_LIST_NAME to "home",
                AnalyticsEvents.ITEMS to listOf(item.toMap()),
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun queueJoin(
        eventId: String,
        outcome: String,
        position: Int?,
    ) {
        val params =
            mutableMapOf<String, Any>(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.OUTCOME to outcome,
                AnalyticsEvents.LANGUAGE to lang(),
            )
        position?.let { AnalyticsEvents.positionBucket(it) }?.let {
            params[AnalyticsEvents.POSITION_BUCKET] = it
        }
        analytics.log(AnalyticsEvents.QUEUE_JOIN, params)
    }

    fun queueTurn(
        eventId: String,
        waitSeconds: Int,
    ) {
        val params =
            mutableMapOf<String, Any>(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.LANGUAGE to lang(),
            )
        AnalyticsEvents.waitBucket(waitSeconds)?.let {
            params[AnalyticsEvents.WAIT_BUCKET] = it
        }
        analytics.log(AnalyticsEvents.QUEUE_TURN, params)
    }

    fun queueClaim(
        eventId: String,
        outcome: String,
    ) {
        analytics.log(
            AnalyticsEvents.QUEUE_CLAIM,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.OUTCOME to outcome,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun login(from: String) {
        analytics.log(
            AnalyticsEvents.LOGIN,
            mapOf(
                AnalyticsEvents.METHOD to AnalyticsEvents.METHOD_EMAIL_CODE,
                AnalyticsEvents.FROM to from,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun signUp(from: String) {
        analytics.log(
            AnalyticsEvents.SIGN_UP,
            mapOf(
                AnalyticsEvents.METHOD to AnalyticsEvents.METHOD_EMAIL_CODE,
                AnalyticsEvents.FROM to from,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun viewItem(
        eventId: String,
        valueRupiah: Long,
        items: List<ItemData>,
    ) {
        analytics.log(
            AnalyticsEvents.VIEW_ITEM,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.CURRENCY to "IDR",
                AnalyticsEvents.VALUE to valueRupiah,
                AnalyticsEvents.ITEMS to items.map { it.toMap() },
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun addToCart(
        eventId: String,
        valueRupiah: Long,
        items: List<ItemData>,
    ) {
        analytics.log(
            AnalyticsEvents.ADD_TO_CART,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.CURRENCY to "IDR",
                AnalyticsEvents.VALUE to valueRupiah,
                AnalyticsEvents.ITEMS to items.map { it.toMap() },
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun holdersComplete(
        eventId: String,
        ticketCount: Int,
        myselfUsed: Boolean,
    ) {
        analytics.log(
            AnalyticsEvents.HOLDERS_COMPLETE,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.TICKET_COUNT to ticketCount,
                AnalyticsEvents.MYSELF_USED to myselfUsed,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun beginCheckout(
        bookingId: String,
        valueRupiah: Long,
        items: List<ItemData>,
    ) {
        analytics.log(
            AnalyticsEvents.BEGIN_CHECKOUT,
            mapOf(
                AnalyticsEvents.BOOKING_ID to bookingId,
                AnalyticsEvents.CURRENCY to "IDR",
                AnalyticsEvents.VALUE to valueRupiah,
                AnalyticsEvents.ITEMS to items.map { it.toMap() },
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun addPaymentInfo(
        valueRupiah: Long,
        paymentType: String,
    ) {
        analytics.log(
            AnalyticsEvents.ADD_PAYMENT_INFO,
            mapOf(
                AnalyticsEvents.CURRENCY to "IDR",
                AnalyticsEvents.VALUE to valueRupiah,
                AnalyticsEvents.PAYMENT_TYPE to paymentType,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    suspend fun purchase(
        orderCode: String,
        valueRupiah: Long,
        items: List<ItemData>,
        paymentType: String,
    ): Boolean {
        ensurePurchasedOrdersLoaded()
        if (orderCode in purchasedOrders) return false
        markOrderPurchased(orderCode)
        analytics.log(
            AnalyticsEvents.PURCHASE,
            mapOf(
                AnalyticsEvents.TRANSACTION_ID to orderCode,
                AnalyticsEvents.CURRENCY to "IDR",
                AnalyticsEvents.VALUE to valueRupiah,
                AnalyticsEvents.ITEMS to items.map { it.toMap() },
                AnalyticsEvents.PAYMENT_TYPE to paymentType,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
        return true
    }

    fun viewTicket(
        bookingId: String,
        ticketCount: Int,
    ) {
        analytics.log(
            AnalyticsEvents.VIEW_TICKET,
            mapOf(
                AnalyticsEvents.BOOKING_ID to bookingId,
                AnalyticsEvents.TICKET_COUNT to ticketCount,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun queueLeave(
        eventId: String,
        position: Int?,
    ) {
        val params =
            mutableMapOf<String, Any>(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.LANGUAGE to lang(),
            )
        position?.let { AnalyticsEvents.positionBucket(it) }?.let {
            params[AnalyticsEvents.POSITION_BUCKET] = it
        }
        analytics.log(AnalyticsEvents.QUEUE_LEAVE, params)
    }

    fun queueRejoin(
        eventId: String,
        reason: String,
    ) {
        analytics.log(
            AnalyticsEvents.QUEUE_REJOIN,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.REASON to reason,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun soldOutSeen(
        eventId: String,
        where: String,
    ) {
        analytics.log(
            AnalyticsEvents.SOLD_OUT_SEEN,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.WHERE to where,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun orderExpired(
        eventId: String,
        step: String,
    ) {
        analytics.log(
            AnalyticsEvents.ORDER_EXPIRED,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.STEP to step,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun nikRejected(
        eventId: String,
        reason: String,
    ) {
        analytics.log(
            AnalyticsEvents.NIK_REJECTED,
            mapOf(
                AnalyticsEvents.EVENT_ID to eventId,
                AnalyticsEvents.REASON to reason,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun paymentFailed(
        bookingId: String,
        paymentType: String,
        reason: String,
    ) {
        analytics.log(
            AnalyticsEvents.PAYMENT_FAILED,
            mapOf(
                AnalyticsEvents.BOOKING_ID to bookingId,
                AnalyticsEvents.PAYMENT_TYPE to paymentType,
                AnalyticsEvents.REASON to reason,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun paymentExpired(bookingId: String) {
        analytics.log(
            AnalyticsEvents.PAYMENT_EXPIRED,
            mapOf(
                AnalyticsEvents.BOOKING_ID to bookingId,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun ticketDownload(
        bookingId: String,
        method: String,
    ) {
        analytics.log(
            AnalyticsEvents.TICKET_DOWNLOAD,
            mapOf(
                AnalyticsEvents.BOOKING_ID to bookingId,
                AnalyticsEvents.METHOD to method,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun ticketAddCalendar(
        bookingId: String,
        via: String,
    ) {
        analytics.log(
            AnalyticsEvents.TICKET_ADD_CALENDAR,
            mapOf(
                AnalyticsEvents.BOOKING_ID to bookingId,
                AnalyticsEvents.VIA to via,
                AnalyticsEvents.LANGUAGE to lang(),
            ),
        )
    }

    fun setAppLanguage(language: String) {
        analytics.setUserProperty(AnalyticsEvents.APP_LANGUAGE, language)
    }

    fun setSignedIn(signedIn: Boolean) {
        analytics.setUserProperty(AnalyticsEvents.SIGNED_IN, signedIn.toString())
    }

    fun setUserId(userId: String?) {
        analytics.setUserId(userId)
    }

    suspend fun hasPurchased(orderCode: String): Boolean {
        ensurePurchasedOrdersLoaded()
        return orderCode in purchasedOrders
    }

    private companion object {
        const val KEY_PURCHASED_ORDERS = "analytics_purchased_orders"
    }
}

/** Item data for GA4 items array. */
data class ItemData(
    val itemId: String,
    val itemName: String,
    val itemCategory: String? = null,
    val itemBrand: String? = null,
    val price: Long? = null,
    val quantity: Int? = null,
) {
    fun toMap(): Map<String, Any> =
        buildMap {
            put(AnalyticsEvents.ITEM_ID, itemId)
            put(AnalyticsEvents.ITEM_NAME, itemName)
            itemCategory?.let { put(AnalyticsEvents.ITEM_CATEGORY, it) }
            itemBrand?.let { put(AnalyticsEvents.ITEM_BRAND, it) }
            price?.let { put(AnalyticsEvents.PRICE, it) }
            quantity?.let { put(AnalyticsEvents.QUANTITY, it) }
        }
}
