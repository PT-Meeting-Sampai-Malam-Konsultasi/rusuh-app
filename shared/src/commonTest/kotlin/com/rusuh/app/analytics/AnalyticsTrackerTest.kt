package com.rusuh.app.analytics

import com.rusuh.app.data.storage.FakeAppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsTrackerTest {
    private fun fixture() = Fixture()

    private class Fixture {
        val prefs = FakeAppPreferences()
        val analytics = FakeAnalytics()
        val languageState = FakeLanguageState()
        val tracker = AnalyticsTracker(analytics, languageState, prefs)
    }

    @Test
    fun viewItemListSendsEventWithItems() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("evt_1", "Concert", "Konser", price = 350_000))
            f.tracker.viewItemList(listOf("evt_1"), items)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.VIEW_ITEM_LIST, name)
            assertEquals("home", params[AnalyticsEvents.ITEM_LIST_NAME])
            assertEquals("id", params[AnalyticsEvents.LANGUAGE])
        }

    @Test
    fun selectItemSendsEventWithSingleItem() =
        runTest {
            val f = fixture()
            val item = ItemData("evt_1", "Concert", "Konser", price = 350_000)
            f.tracker.selectItem("evt_1", item)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.SELECT_ITEM, name)
            assertEquals("home", params[AnalyticsEvents.ITEM_LIST_NAME])
        }

    @Test
    fun queueJoinSendsPositionBucket() =
        runTest {
            val f = fixture()
            f.tracker.queueJoin("evt_1", AnalyticsEvents.OUTCOME_JOINED, 150)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.QUEUE_JOIN, name)
            assertEquals("evt_1", params[AnalyticsEvents.EVENT_ID])
            assertEquals(AnalyticsEvents.OUTCOME_JOINED, params[AnalyticsEvents.OUTCOME])
            assertEquals("101-1000", params[AnalyticsEvents.POSITION_BUCKET])
        }

    @Test
    fun queueTurnSendsWaitBucket() =
        runTest {
            val f = fixture()
            f.tracker.queueTurn("evt_1", 120)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.QUEUE_TURN, name)
            assertEquals("61-300", params[AnalyticsEvents.WAIT_BUCKET])
        }

    @Test
    fun queueClaimSendsOutcome() =
        runTest {
            val f = fixture()
            f.tracker.queueClaim("evt_1", AnalyticsEvents.OUTCOME_ADMITTED)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.QUEUE_CLAIM, name)
            assertEquals(AnalyticsEvents.OUTCOME_ADMITTED, params[AnalyticsEvents.OUTCOME])
        }

    @Test
    fun loginSendsMethodAndFrom() =
        runTest {
            val f = fixture()
            f.tracker.login(AnalyticsEvents.FROM_QUEUE)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.LOGIN, name)
            assertEquals(AnalyticsEvents.METHOD_EMAIL_CODE, params[AnalyticsEvents.METHOD])
            assertEquals(AnalyticsEvents.FROM_QUEUE, params[AnalyticsEvents.FROM])
        }

    @Test
    fun signUpSendsMethodAndFrom() =
        runTest {
            val f = fixture()
            f.tracker.signUp(AnalyticsEvents.FROM_ACCOUNT)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.SIGN_UP, name)
            assertEquals(AnalyticsEvents.METHOD_EMAIL_CODE, params[AnalyticsEvents.METHOD])
            assertEquals(AnalyticsEvents.FROM_ACCOUNT, params[AnalyticsEvents.FROM])
        }

    @Test
    fun viewItemSendsValueAndItems() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("cat_1", "Festival A", "evt_1", "Concert", 850_000))
            f.tracker.viewItem("evt_1", 850_000, items)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.VIEW_ITEM, name)
            assertEquals("IDR", params[AnalyticsEvents.CURRENCY])
            assertEquals(850_000L, params[AnalyticsEvents.VALUE])
        }

    @Test
    fun addToCartSendsValueAndItems() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("cat_1", "Festival A", "evt_1", "Concert", 850_000, 2))
            f.tracker.addToCart("evt_1", 1_700_000, items)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.ADD_TO_CART, name)
            assertEquals(1_700_000L, params[AnalyticsEvents.VALUE])
        }

    @Test
    fun holdersCompleteSendsTicketCountAndMyselfUsed() =
        runTest {
            val f = fixture()
            f.tracker.holdersComplete("evt_1", 2, myselfUsed = true)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.HOLDERS_COMPLETE, name)
            assertEquals(2, params[AnalyticsEvents.TICKET_COUNT])
            assertEquals(true, params[AnalyticsEvents.MYSELF_USED])
        }

    @Test
    fun beginCheckoutSendsBookingIdAndValue() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("cat_1", "Festival A", "evt_1", "Concert", 850_000, 2))
            f.tracker.beginCheckout("bk_123", 1_700_000, items)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.BEGIN_CHECKOUT, name)
            assertEquals("bk_123", params[AnalyticsEvents.BOOKING_ID])
            assertEquals(1_700_000L, params[AnalyticsEvents.VALUE])
        }

    @Test
    fun addPaymentInfoSendsPaymentType() =
        runTest {
            val f = fixture()
            f.tracker.addPaymentInfo(1_700_000, AnalyticsEvents.PAYMENT_QRIS)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.ADD_PAYMENT_INFO, name)
            assertEquals(AnalyticsEvents.PAYMENT_QRIS, params[AnalyticsEvents.PAYMENT_TYPE])
        }

    @Test
    fun purchaseSendsOncePerOrderCode() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("cat_1", "Festival A", "evt_1", "Concert", 850_000, 2))

            val sent1 = f.tracker.purchase("RSH-26-001", 1_700_000, items, AnalyticsEvents.PAYMENT_QRIS)
            val sent2 = f.tracker.purchase("RSH-26-001", 1_700_000, items, AnalyticsEvents.PAYMENT_QRIS)

            assertTrue(sent1)
            assertFalse(sent2)
            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.PURCHASE, name)
            assertEquals("RSH-26-001", params[AnalyticsEvents.TRANSACTION_ID])
        }

    @Test
    fun purchasePersistsOrderCodeAcrossInstances() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("cat_1", "Festival A", "evt_1", "Concert", 850_000, 2))

            f.tracker.purchase("RSH-26-002", 1_700_000, items, AnalyticsEvents.PAYMENT_QRIS)

            val tracker2 = AnalyticsTracker(f.analytics, f.languageState, f.prefs)
            val sent = tracker2.purchase("RSH-26-002", 1_700_000, items, AnalyticsEvents.PAYMENT_QRIS)

            assertFalse(sent)
            assertEquals(1, f.analytics.events.size)
        }

    @Test
    fun viewTicketSendsBookingIdAndCount() =
        runTest {
            val f = fixture()
            f.tracker.viewTicket("bk_123", 2)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.VIEW_TICKET, name)
            assertEquals("bk_123", params[AnalyticsEvents.BOOKING_ID])
            assertEquals(2, params[AnalyticsEvents.TICKET_COUNT])
        }

    @Test
    fun queueLeaveSendsPositionBucket() =
        runTest {
            val f = fixture()
            f.tracker.queueLeave("evt_1", 50)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.QUEUE_LEAVE, name)
            assertEquals("1-100", params[AnalyticsEvents.POSITION_BUCKET])
        }

    @Test
    fun queueRejoinSendsReason() =
        runTest {
            val f = fixture()
            f.tracker.queueRejoin("evt_1", AnalyticsEvents.REASON_MISSED)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.QUEUE_REJOIN, name)
            assertEquals(AnalyticsEvents.REASON_MISSED, params[AnalyticsEvents.REASON])
        }

    @Test
    fun soldOutSeenSendsWhere() =
        runTest {
            val f = fixture()
            f.tracker.soldOutSeen("evt_1", AnalyticsEvents.WHERE_QUEUE)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.SOLD_OUT_SEEN, name)
            assertEquals(AnalyticsEvents.WHERE_QUEUE, params[AnalyticsEvents.WHERE])
        }

    @Test
    fun orderExpiredSendsStep() =
        runTest {
            val f = fixture()
            f.tracker.orderExpired("evt_1", AnalyticsEvents.STEP_HOLDERS)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.ORDER_EXPIRED, name)
            assertEquals(AnalyticsEvents.STEP_HOLDERS, params[AnalyticsEvents.STEP])
        }

    @Test
    fun nikRejectedSendsReasonWithoutNik() =
        runTest {
            val f = fixture()
            f.tracker.nikRejected("evt_1", AnalyticsEvents.REASON_NIK_INVALID)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.NIK_REJECTED, name)
            assertEquals(AnalyticsEvents.REASON_NIK_INVALID, params[AnalyticsEvents.REASON])
        }

    @Test
    fun paymentFailedSendsReasonAndType() =
        runTest {
            val f = fixture()
            f.tracker.paymentFailed("bk_123", AnalyticsEvents.PAYMENT_GOPAY, "CANCELLED")

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.PAYMENT_FAILED, name)
            assertEquals("bk_123", params[AnalyticsEvents.BOOKING_ID])
            assertEquals(AnalyticsEvents.PAYMENT_GOPAY, params[AnalyticsEvents.PAYMENT_TYPE])
            assertEquals("CANCELLED", params[AnalyticsEvents.REASON])
        }

    @Test
    fun paymentExpiredSendsBookingId() =
        runTest {
            val f = fixture()
            f.tracker.paymentExpired("bk_123")

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.PAYMENT_EXPIRED, name)
            assertEquals("bk_123", params[AnalyticsEvents.BOOKING_ID])
        }

    @Test
    fun ticketDownloadSendsMethod() =
        runTest {
            val f = fixture()
            f.tracker.ticketDownload("bk_123", AnalyticsEvents.METHOD_PDF)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.TICKET_DOWNLOAD, name)
            assertEquals(AnalyticsEvents.METHOD_PDF, params[AnalyticsEvents.METHOD])
        }

    @Test
    fun ticketAddCalendarSendsVia() =
        runTest {
            val f = fixture()
            f.tracker.ticketAddCalendar("bk_123", AnalyticsEvents.VIA_DEVICE)

            assertEquals(1, f.analytics.events.size)
            val (name, params) = f.analytics.events[0]
            assertEquals(AnalyticsEvents.TICKET_ADD_CALENDAR, name)
            assertEquals(AnalyticsEvents.VIA_DEVICE, params[AnalyticsEvents.VIA])
        }

    @Test
    fun setAppLanguageSetsUserProperty() =
        runTest {
            val f = fixture()
            f.tracker.setAppLanguage("en")

            assertEquals("en", f.analytics.userProperties[AnalyticsEvents.APP_LANGUAGE])
        }

    @Test
    fun setSignedInSetsUserProperty() =
        runTest {
            val f = fixture()
            f.tracker.setSignedIn(true)
            assertEquals("true", f.analytics.userProperties[AnalyticsEvents.SIGNED_IN])

            f.tracker.setSignedIn(false)
            assertEquals("false", f.analytics.userProperties[AnalyticsEvents.SIGNED_IN])
        }

    @Test
    fun setUserIdCallsAnalytics() =
        runTest {
            val f = fixture()
            f.tracker.setUserId("usr_123")
            assertEquals("usr_123", f.analytics.recordedUserId)

            f.tracker.setUserId(null)
            assertEquals(null, f.analytics.recordedUserId)
        }

    @Test
    fun hasPurchasedReturnsTrueAfterPurchase() =
        runTest {
            val f = fixture()
            val items = listOf(ItemData("cat_1", "Festival A"))

            assertFalse(f.tracker.hasPurchased("RSH-26-003"))
            f.tracker.purchase("RSH-26-003", 1_700_000, items, AnalyticsEvents.PAYMENT_QRIS)
            assertTrue(f.tracker.hasPurchased("RSH-26-003"))
        }
}

private class FakeLanguageState : com.rusuh.app.data.network.LocaleProvider {
    var currentLanguage = com.rusuh.app.data.network.AppLanguage.Indonesian
    val language = MutableStateFlow(currentLanguage)

    override fun language() = currentLanguage
}
