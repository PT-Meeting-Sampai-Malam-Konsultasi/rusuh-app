package com.rusuh.app.ui.components

import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.ic_tab_account
import rusuh_app.shared.generated.resources.ic_tab_events
import rusuh_app.shared.generated.resources.ic_tab_queues
import rusuh_app.shared.generated.resources.ic_tab_tickets
import rusuh_app.shared.generated.resources.tab_account
import rusuh_app.shared.generated.resources.tab_event
import rusuh_app.shared.generated.resources.tab_queues
import rusuh_app.shared.generated.resources.tab_tickets

/** The four top-level destinations shown as bottom navigation tabs. */
enum class RusuhTab(val label: StringResource, val icon: DrawableResource) {
    Event(Res.string.tab_event, Res.drawable.ic_tab_events),
    Antrian(Res.string.tab_queues, Res.drawable.ic_tab_queues),
    Tiket(Res.string.tab_tickets, Res.drawable.ic_tab_tickets),
    Akun(Res.string.tab_account, Res.drawable.ic_tab_account),
}
