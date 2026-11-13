package com.rusuh.app.data.stub

import io.ktor.http.HttpMethod
import io.ktor.http.decodeURLPart

/**
 * One canned HTTP response. [body] may hold `@{now}` placeholders, resolved by [StubClock.render] when the
 * [StubEngine] serves it. A null [contentType] sends none (e.g. a 204).
 */
data class StubResponse(
    val status: Int,
    val body: String = "",
    val contentType: String? = JSON,
    val headers: Map<String, String> = emptyMap(),
) {
    companion object {
        const val JSON = "application/json"
        const val PROBLEM_JSON = "application/problem+json"

        fun ok(body: String) = StubResponse(200, body)

        fun problem(
            status: Int,
            body: String,
            headers: Map<String, String> = emptyMap(),
        ) = StubResponse(status, body, PROBLEM_JSON, headers)
    }
}

/** A stubbed endpoint: [template] is the spec path with `{param}` segments, e.g. `/queue/{eventId}/frontier`. */
data class StubRoute(
    val method: HttpMethod,
    val template: String,
    val response: StubResponse,
) {
    /** Number of fixed segments, so `/bookings/active` wins over `/bookings/{bookingId}`. */
    internal val literalSegments: Int = segments(template).count { !it.isParam() }

    /**
     * The percent-decoded values of the `{param}` segments when [path] matches this route, otherwise null. The
     * query string is ignored: filters such as `?status=` never change which stub answers.
     */
    fun match(path: String): Map<String, String>? {
        val pairs = segments(template).zip(segments(path))
        val sameLength = segments(template).size == segments(path).size
        val fits = pairs.all { (expected, actual) -> expected.isParam() || expected == actual }
        return if (sameLength && fits) {
            pairs.filter { (expected, _) -> expected.isParam() }
                .associate { (expected, actual) -> expected.removeSurrounding("{", "}") to actual.decodeURLPart() }
        } else {
            null
        }
    }

    private fun String.isParam() = startsWith("{") && endsWith("}")

    internal companion object {
        fun segments(path: String): List<String> = path.substringBefore('?').split('/').filter { it.isNotEmpty() }
    }
}

/** A route that matched a request, with the values of its path parameters. */
data class StubMatch(
    val route: StubRoute,
    val pathParams: Map<String, String>,
)

/** Default success response of every stubbed endpoint, from [StubJson]. */
object StubResponses {
    val routes: List<StubRoute> =
        listOf(
            StubRoute(HttpMethod.Get, "/core/events", StubResponse.ok(StubJson.EVENTS)),
            StubRoute(HttpMethod.Get, "/core/events/{eventId}", StubResponse.ok(StubJson.EVENT_DETAIL)),
            StubRoute(HttpMethod.Get, "/core/events/{eventId}/categories", StubResponse.ok(StubJson.CATEGORIES)),
            StubRoute(
                HttpMethod.Post,
                "/core/events/{eventId}/attendees/validate",
                StubResponse.ok(StubJson.ATTENDEE_VALIDATION),
            ),
            StubRoute(HttpMethod.Post, "/core/bookings", StubResponse(201, StubJson.BOOKING_HELD)),
            StubRoute(HttpMethod.Get, "/core/bookings/active", StubResponse.ok(StubJson.BOOKINGS_ACTIVE)),
            StubRoute(HttpMethod.Get, "/core/bookings/{bookingId}", StubResponse.ok(StubJson.BOOKING_PAID)),
            StubRoute(HttpMethod.Get, "/core/tickets", StubResponse.ok(StubJson.TICKETS)),
            StubRoute(HttpMethod.Get, "/core/tickets/{ticketId}", StubResponse.ok(StubJson.TICKET_DETAIL)),
            StubRoute(HttpMethod.Post, "/queue/{eventId}/join", StubResponse.ok(StubJson.QUEUE_JOIN)),
            StubRoute(HttpMethod.Get, "/queue/{eventId}/me", StubResponse.ok(StubJson.QUEUE_ME)),
            StubRoute(HttpMethod.Get, "/queue/{eventId}/frontier", StubResponse.ok(StubJson.QUEUE_FRONTIER)),
            StubRoute(HttpMethod.Post, "/queue/{eventId}/claim", StubResponse.ok(StubJson.QUEUE_CLAIM)),
            StubRoute(HttpMethod.Post, "/auth/otp", StubResponse(202, StubJson.AUTH_OTP)),
            StubRoute(HttpMethod.Post, "/auth/otp/verify", StubResponse.ok(StubJson.AUTH_VERIFY)),
            StubRoute(HttpMethod.Post, "/auth/refresh", StubResponse.ok(StubJson.AUTH_REFRESH)),
            StubRoute(HttpMethod.Get, "/auth/profile", StubResponse.ok(StubJson.ME)),
            StubRoute(HttpMethod.Put, "/auth/profile", StubResponse.ok(StubJson.ME)),
            StubRoute(HttpMethod.Post, "/auth/logout", StubResponse(204, contentType = null)),
            StubRoute(HttpMethod.Post, "/queue/devices", StubResponse(201, StubJson.DEVICE)),
            StubRoute(
                HttpMethod.Post,
                "/core/bookings/{bookingId}/cancel",
                StubResponse.ok(StubJson.BOOKING_CANCELLED),
            ),
            StubRoute(HttpMethod.Post, "/core/bookings/{bookingId}/payment", StubResponse.ok(StubJson.BOOKING_PAYMENT)),
            StubRoute(
                HttpMethod.Post,
                "/core/bookings/{bookingId}/check-status",
                StubResponse.ok(StubJson.CHECK_STATUS),
            ),
            StubRoute(HttpMethod.Delete, "/queue/{eventId}/me", StubResponse(204, contentType = null)),
        )

    /** True when some route serves [path] under another method (a request here is a 405, not a 404). */
    fun pathExists(
        path: String,
        candidates: List<StubRoute> = routes,
    ): Boolean = candidates.any { it.match(path) != null }

    /** The best route for [method] and [path] among [candidates] (most fixed segments wins), or null. */
    fun find(
        method: HttpMethod,
        path: String,
        candidates: List<StubRoute> = routes,
    ): StubMatch? =
        candidates
            .filter { it.method == method }
            .mapNotNull { route -> route.match(path)?.let { StubMatch(route, it) } }
            .maxByOrNull { it.route.literalSegments }
}
