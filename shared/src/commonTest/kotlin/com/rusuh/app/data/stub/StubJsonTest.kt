package com.rusuh.app.data.stub

import com.rusuh.app.data.network.ApiClient
import com.rusuh.app.data.remote.dto.ProblemDetails
import com.rusuh.app.domain.model.ErrorCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Decodes every stub, so a spec change that is not mirrored in [StubJson] fails here. */
class StubJsonTest {
    private val clock = StubClock()

    private fun obj(body: String): JsonObject = Json.parseToJsonElement(clock.render(body)).jsonObject

    private fun JsonObject.keys(vararg names: String) {
        names.forEach { assertTrue(it in this, "missing member '$it' in $keys") }
    }

    @Test
    fun everyRouteBodyIsValidJsonAfterRendering() {
        StubResponses.routes.forEach { route ->
            val rendered = clock.render(route.response.body)
            assertTrue("@{" !in rendered, "unresolved placeholder in ${route.template}")
            if (route.response.status == 204) {
                assertTrue(rendered.isEmpty(), "a 204 has no body: ${route.template}")
            } else {
                Json.parseToJsonElement(rendered)
            }
        }
    }

    @Test
    fun everyErrorStubDecodesAsProblemDetails() {
        StubError.entries.forEach { error ->
            val problem = ApiClient.defaultJson.decodeFromString(ProblemDetails.serializer(), error.response.body)
            assertEquals(StubResponse.PROBLEM_JSON, error.response.contentType, "$error content type")
            assertEquals(error.response.status, problem.status, "$error status")
            assertNotEquals(ErrorCode.UNKNOWN, problem.code, "$error has a code the app does not know")
            assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", problem.traceId)
        }
    }

    @Test
    fun errorStubsKeepTheirExtras() {
        fun problem(error: StubError) =
            ApiClient.defaultJson.decodeFromString(ProblemDetails.serializer(), error.response.body)

        assertEquals(JsonPrimitive("cat_festival_b"), problem(StubError.QuantityOverReservation).extras["categoryId"])
        assertEquals(JsonPrimitive("cat_vip"), problem(StubError.CategorySoldOut).extras["categoryId"])
        assertEquals(JsonPrimitive(3), problem(StubError.WrongCode).extras["attemptsLeft"])
    }

    @Test
    fun retryAfterIsOnlyOnTheBackOffErrors() {
        assertEquals("30", StubError.RateLimited.response.headers["Retry-After"])
        assertEquals("10", StubError.ServiceUnavailable.response.headers["Retry-After"])
        assertTrue(StubError.NikSeatTaken.response.headers.isEmpty())
    }

    @Test
    fun eventsAndCategories() {
        val events = obj(StubJson.EVENTS)
        events.keys("items", "serverNow")
        val first = events.getValue("items").jsonArray.first().jsonObject
        first.keys("eventId", "name", "state", "startsAt", "saleOpensAt", "priceFrom", "capacity")
        assertEquals(2, events.getValue("items").jsonArray.size)

        val categories = obj(StubJson.CATEGORIES)
        categories.keys("eventId", "categories", "maxTicketsPerOrder", "adminFee", "expiresAt", "serverNow")
        categories.getValue("categories").jsonArray.forEach {
            it.jsonObject.keys("categoryId", "name", "seating", "entryGate", "price", "availability", "available")
        }
    }

    @Test
    fun bookingsAndTickets() {
        val held = obj(StubJson.BOOKING_HELD)
        held.keys("bookingId", "status", "price", "orderCode", "attendees", "expiresAt", "serverNow")
        held.getValue("price").jsonObject.keys("lines", "ticketSubtotal", "adminFee", "total")
        assertEquals(JsonArray(emptyList()), held.getValue("tickets"))

        val paid = obj(StubJson.BOOKING_PAID)
        assertEquals("PAID", paid.getValue("status").jsonPrimitive.content)
        paid.getValue("tickets").jsonArray.forEach {
            it.jsonObject.keys("ticketId", "ticketCode", "holderName", "qrPayload", "issuedAt")
        }
        assertEquals(2, obj(StubJson.TICKETS).getValue("items").jsonArray.size)
        obj(StubJson.PAYMENT_STATUS).keys("bookingStatus", "paymentStatus", "returnUrl", "serverNow")
    }

    @Test
    fun queueAndAuthPayloads() {
        obj(StubJson.QUEUE_JOIN).keys("eventId", "state", "seq", "served", "queueToken", "serverNow")
        obj(StubJson.QUEUE_ME).keys("seq", "served", "turnExpiresAt", "queueToken")
        obj(StubJson.QUEUE_FRONTIER).keys("event", "state", "served", "sellable", "ratePerMin", "opensAt", "ts")
        obj(StubJson.QUEUE_CLAIM).keys("admissionToken", "slotId", "maxTickets", "expiresAt", "serverNow")
        obj(StubJson.AUTH_OTP).keys("challengeId", "expiresAt", "resendAfterSeconds")
        obj(StubJson.AUTH_VERIFY).keys("accessToken", "expiresIn", "refreshToken", "user")
        obj(StubJson.AUTH_REFRESH).keys("accessToken", "refreshToken")
        obj(StubJson.ME).keys("userId", "email", "fullName", "locale")
        assertEquals(900, obj(StubJson.AUTH_VERIFY).getValue("expiresIn").jsonPrimitive.int)
    }

    @Test
    fun paymentPayloadsAndActions() {
        obj(StubJson.DEVICE).keys("deviceToken", "expiresAt")
        obj(StubJson.PAYMENT_SESSION).keys("handoffCode", "expiresAt", "payUrl")
        obj(StubJson.BOOKING_PAYMENT).keys("bookingId", "snapToken", "paymentExpiresAt", "redirectUrl", "serverNow")
        obj(StubJson.PAYMENT_SUMMARY).keys("bookingId", "orderCode", "price", "expiresAt", "csrfToken")
        obj(StubJson.PAYMENT_SNAP).keys("attemptId", "orderId", "method", "snapToken", "gatewayExpiresAt")
        assertEquals("CANCELLED", obj(StubJson.BOOKING_CANCELLED).getValue("status").jsonPrimitive.content)
    }

    @Test
    fun enumValuesStayInsideTheSpecLists() {
        // Lists copied from the contract's EventState, QueueState, BookingStatus, PaymentStatus and PaymentMethod.
        val eventStates =
            setOf("coming_soon", "waiting_room_open", "on_sale", "almost_gone", "temporarily_full", "sold_out", "ended")
        val queueStates = setOf("preopen", "open", "paused", "closed")
        val bookingStatuses = setOf("SELECTION", "PAYMENT_PENDING", "HELD", "PAID", "EXPIRED", "CANCELLED")
        val paymentStatuses =
            setOf("none", "pending", "settled", "denied", "expired", "cancelled", "failed", "paid_after_expiry")
        val paymentMethods =
            setOf(
                "qris", "gopay", "shopeepay", "bca_va", "mandiri_va",
                "bni_va", "bri_va", "permata_va", "cimb_va", "card",
            )

        fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content

        obj(StubJson.EVENTS).getValue("items").jsonArray.forEach {
            assertTrue(it.jsonObject.text("state") in eventStates)
        }
        listOf(StubJson.QUEUE_JOIN, StubJson.QUEUE_ME, StubJson.QUEUE_FRONTIER).forEach {
            assertTrue(obj(it).text("state") in queueStates)
        }
        listOf(
            StubJson.BOOKING_HELD,
            StubJson.BOOKING_PAID,
            StubJson.BOOKING_CANCELLED,
            StubJson.CHECK_STATUS,
        ).forEach {
            assertTrue(obj(it).text("status") in bookingStatuses)
        }
        assertTrue(obj(StubJson.PAYMENT_STATUS).text("bookingStatus") in bookingStatuses)
        assertTrue(obj(StubJson.PAYMENT_STATUS).text("paymentStatus") in paymentStatuses)
        assertTrue(obj(StubJson.BOOKING_PAID).text("paymentMethod") in paymentMethods)
        assertTrue(obj(StubJson.PAYMENT_SNAP).text("method") in paymentMethods)
    }

    @Test
    fun stubPayloadsCarryNoRealPersonalData() {
        val all = StubResponses.routes.joinToString { it.response.body }
        // Only masked NIKs (12 digits hidden) and example.com addresses.
        assertTrue(Regex("""\d{16}""").find(all) == null, "a full 16-digit NIK is in a stub")
        Regex("""[\w.]+@[\w.]+""").findAll(all).forEach { assertTrue(it.value.endsWith("example.com")) }
    }
}
