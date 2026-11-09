package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import com.rusuh.app.ui.theme.StatusColorPair
import org.jetbrains.compose.resources.StringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.event_card_status_almost_gone
import rusuh_app.shared.generated.resources.event_card_status_ended
import rusuh_app.shared.generated.resources.event_card_status_on_sale
import rusuh_app.shared.generated.resources.status_available
import rusuh_app.shared.generated.resources.status_coming_soon
import rusuh_app.shared.generated.resources.status_sold_out
import rusuh_app.shared.generated.resources.status_temporarily_full

/**
 * Availability status of an event, as shown on the home page event cards.
 *
 * Each variant carries the data its badge needs so the UI never has to reach
 * back into the event model. Keep rendering-free so it can be unit-tested in
 * commonTest without a Compose harness.
 */
sealed interface EventStatus {
    /** Sale not open yet; the waiting room opens first. Label: "Antrian dibuka" (Figma `waiting_room_open`). */
    data object WaitingRoomOpen : EventStatus

    /** Tickets are on sale and can be bought now (Figma `on_sale`). */
    data object OnSale : EventStatus

    /** Few tickets left (Figma `almost_gone`). Still purchasable. */
    data object AlmostGone : EventStatus

    /** Tickets held by other buyers; may free up. Label: "Penuh sementara". */
    data object TemporarilyFull : EventStatus

    /**
     * Everything sold. Shows how fast it went and the venue capacity.
     *
     * @param soldOutInMinutes minutes from sale-open to sell-out (e.g. 41).
     * @param capacity total tickets sold / venue capacity (e.g. 12_000).
     */
    data class SoldOut(val soldOutInMinutes: Int, val capacity: Int) : EventStatus {
        init {
            require(soldOutInMinutes >= 0) { "soldOutInMinutes must be >= 0, was $soldOutInMinutes" }
            require(capacity > 0) { "capacity must be > 0, was $capacity" }
        }
    }

    /**
     * Not on sale yet. Shows when sales open and a countdown to it.
     *
     * @param countdown pre-formatted remaining time (e.g. "7 hari 14 jam lagi").
     *   Pre-formatted by the caller so this stays locale/clock agnostic.
     * @param opensAt pre-formatted sale-open moment (e.g. "Jum, 30 Okt · 10.00 WIB"); null to omit.
     */
    data class ComingSoon(val countdown: String, val opensAt: String? = null) : EventStatus {
        init {
            require(countdown.isNotBlank()) { "countdown must not be blank" }
        }
    }

    /**
     * Online sales are over.
     *
     * @param closedOn pre-formatted close date (e.g. "29 Okt").
     */
    data class Ended(val closedOn: String) : EventStatus {
        init {
            require(closedOn.isNotBlank()) { "closedOn must not be blank" }
        }
    }
}

/** True when the event can no longer be opened from the list (sold out or ended). */
val EventStatus.isDisabled: Boolean
    get() = this is EventStatus.SoldOut || this is EventStatus.Ended

/**
 * Resolves an [EventStatus] to the string resource for its short stamp label.
 * Pure function — returns the [StringResource] key (a plain value, not a
 * composable), so it stays unit-testable and the composable call site resolves
 * it with `stringResource(...)`.
 */
fun EventStatus.badgeLabel(): StringResource =
    when (this) {
        EventStatus.WaitingRoomOpen -> Res.string.status_available
        EventStatus.OnSale -> Res.string.event_card_status_on_sale
        EventStatus.AlmostGone -> Res.string.event_card_status_almost_gone
        EventStatus.TemporarilyFull -> Res.string.status_temporarily_full
        is EventStatus.SoldOut -> Res.string.status_sold_out
        is EventStatus.ComingSoon -> Res.string.status_coming_soon
        is EventStatus.Ended -> Res.string.event_card_status_ended
    }

/** Semantic color set for the status. Pure function — unit-tested directly. */
fun EventStatus.badgeColors(): StatusColorPair =
    when (this) {
        EventStatus.WaitingRoomOpen -> RusuhStatusColors.waitingRoomOpen
        EventStatus.OnSale -> RusuhStatusColors.onSale
        EventStatus.AlmostGone -> RusuhStatusColors.almostGone
        EventStatus.TemporarilyFull -> RusuhStatusColors.temporarilyFull
        is EventStatus.SoldOut -> RusuhStatusColors.soldOutStamp
        is EventStatus.ComingSoon -> RusuhStatusColors.upcomingStamp
        is EventStatus.Ended -> RusuhStatusColors.ended
    }
