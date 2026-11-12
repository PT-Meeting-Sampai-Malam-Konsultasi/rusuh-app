package com.rusuh.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ETicketRouteTest {
    @Test
    fun plainOrderIdIsKeptAsIs() {
        assertEquals("eticket/RSH-26-084731", Screen.ETicket.routeFor("RSH-26-084731"))
    }

    @Test
    fun reservedCharactersAreEncodedSoTheIdStaysOneSegment() {
        val route = Screen.ETicket.routeFor("a/b?c=d e#f")
        assertEquals("eticket/a%2Fb%3Fc%3Dd%20e%23f", route)
        assertFalse(route.removePrefix("eticket/").contains('/'))
    }

    @Test
    fun nonAsciiIsEncodedAsUtf8Bytes() {
        assertEquals("caf%C3%A9", encodePathSegment("café"))
    }

    @Test
    fun routePatternHasTheOrderIdArgument() {
        assertEquals("eticket/{orderId}", Screen.ETicket.route)
        assertEquals("orderId", Screen.ETicket.ARG_ORDER_ID)
    }
}
