package com.rusuh.app.ui.components

import com.rusuh.app.ui.theme.RusuhStatusColors
import com.rusuh.app.ui.theme.StatusColorPair
import org.jetbrains.compose.resources.StringResource
import rusuh_app.shared.generated.resources.Res
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
    /** Tickets are on sale and can be bought now. */
    data object Available : EventStatus

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
     * Not on sale yet. Shows a countdown to when sales open.
     *
     * @param countdown pre-formatted remaining time (e.g. "7 hari 14 jam lagi").
     *   Pre-formatted by the caller so this stays locale/clock agnostic.
     */
    data class ComingSoon(val countdown: String) : EventStatus {
        init {
            require(countdown.isNotBlank()) { "countdown must not be blank" }
        }
    }
}

/**
 * Resolves an [EventStatus] to the string resource for its short pill label.
 * Pure function — returns the [StringResource] key (a plain value, not a
 * composable), so it stays unit-testable and the composable call site resolves
 * it with `stringResource(...)`.
 */
fun EventStatus.badgeLabel(): StringResource =
    when (this) {
        EventStatus.Available -> Res.string.status_available
        EventStatus.TemporarilyFull -> Res.string.status_temporarily_full
        is EventStatus.SoldOut -> Res.string.status_sold_out
        is EventStatus.ComingSoon -> Res.string.status_coming_soon
    }

/** Semantic color pair for the status. Pure function — unit-tested directly. */
fun EventStatus.badgeColors(): StatusColorPair =
    when (this) {
        EventStatus.Available -> RusuhStatusColors.available
        EventStatus.TemporarilyFull -> RusuhStatusColors.limited
        is EventStatus.SoldOut -> RusuhStatusColors.soldOut
        is EventStatus.ComingSoon -> RusuhStatusColors.upcoming
    }
