package com.rusuh.app.navigation

import com.rusuh.app.ui.components.RusuhTab
import kotlin.test.Test
import kotlin.test.assertEquals

class RusuhTabRouteTest {
    @Test
    fun eachTabMapsToItsTopLevelRoute() {
        assertEquals(Screen.Home.route, RusuhTab.Event.destinationRoute())
        assertEquals(Screen.Queues.route, RusuhTab.Antrian.destinationRoute())
        assertEquals(Screen.Tickets.route, RusuhTab.Tiket.destinationRoute())
        assertEquals(Screen.Account.route, RusuhTab.Akun.destinationRoute())
    }

    @Test
    fun allFourTabsMapToDistinctRoutes() {
        val routes = RusuhTab.entries.map { it.destinationRoute() }
        assertEquals(RusuhTab.entries.size, routes.toSet().size)
    }

    @Test
    fun tabOrderMatchesHiFiBottomBar() {
        assertEquals(
            listOf("Event", "Antrian", "Tiket", "Akun"),
            RusuhTab.entries.map { it.label },
        )
    }

    @Test
    fun waitingRoomRouteCarriesEventId() {
        assertEquals("waiting-room/evt-samudra", Screen.WaitingRoom.routeFor("evt-samudra"))
    }
}
