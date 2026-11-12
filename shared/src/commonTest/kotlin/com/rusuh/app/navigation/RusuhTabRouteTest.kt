package com.rusuh.app.navigation

import com.rusuh.app.ui.components.RusuhTab
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.tab_account
import rusuh_app.shared.generated.resources.tab_event
import rusuh_app.shared.generated.resources.tab_queues
import rusuh_app.shared.generated.resources.tab_tickets
import kotlin.test.Test
import kotlin.test.assertEquals

class RusuhTabRouteTest {
    @Test
    fun eachTabMapsToItsTopLevelRoute() {
        assertEquals(Screen.Home.route, RusuhTab.Event.destinationRoute())
        assertEquals(Screen.Queues.route, RusuhTab.Antrian.destinationRoute())
        assertEquals(Screen.Tickets.route, RusuhTab.Tiket.destinationRoute())
        assertEquals(Screen.Account.route, RusuhTab.Akun.destinationRoute())
        assertEquals("sign-in", Screen.SignIn.routeFor())
        assertEquals("sign-in?eventId=e1", Screen.SignIn.routeFor("e1"))
    }

    @Test
    fun allFourTabsMapToDistinctRoutes() {
        val routes = RusuhTab.entries.map { it.destinationRoute() }
        assertEquals(RusuhTab.entries.size, routes.toSet().size)
    }

    @Test
    fun tabOrderMatchesHiFiBottomBar() {
        assertEquals(
            listOf(Res.string.tab_event, Res.string.tab_queues, Res.string.tab_tickets, Res.string.tab_account),
            RusuhTab.entries.map { it.label },
        )
    }

    @Test
    fun waitingRoomRouteCarriesEventId() {
        assertEquals("waiting-room/evt-samudra", Screen.WaitingRoom.routeFor("evt-samudra"))
    }
}
