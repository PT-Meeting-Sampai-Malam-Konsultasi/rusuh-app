package com.rusuh.app.feature.detail

import androidx.compose.runtime.Immutable
import com.rusuh.app.domain.model.BookingRequest
import com.rusuh.app.domain.model.BookingTicket
import com.rusuh.app.domain.model.DetailOffering
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviState
import com.rusuh.app.ui.components.SeatAvailability
import com.rusuh.app.ui.components.SeatCategoryUi

/** Stock at/below which a category shows "HAMPIR HABIS". */
internal const val ALMOST_GONE_STOCK = 10

/** Why something failed; the host maps each to localized copy. */
enum class DetailError {
    /** Event/categories could not be loaded. */
    LoadFailed,

    /** The booking could not be created. */
    BookingFailed,

    /** An attendee NIK was rejected by the server (TODO(FR-15-M1 #80)). */
    NikRejected,
}

/** Lifecycle of the Detail page. */
sealed interface DetailPhase {
    data object Loading : DetailPhase

    data object Ready : DetailPhase

    /** Loaded but the event has no categories to sell. */
    data object Empty : DetailPhase

    data class Failed(val error: DetailError) : DetailPhase

    /** The order window elapsed (remaining reached 0). */
    data object Expired : DetailPhase
}

/**
 * MVI state of the Detail Ticket page.
 *
 * @property expiresAtMillis end of the order window in server epoch millis.
 * @property remainingSeconds seconds left, recomputed from the server clock on every tick.
 */
@Immutable
data class DetailState(
    val phase: DetailPhase = DetailPhase.Loading,
    val detail: DetailContent? = null,
    val form: DetailFormState = DetailFormState(),
    val expiresAtMillis: Long = 0L,
    val remainingSeconds: Int = ORDER_WINDOW_SECONDS,
    val isSubmitting: Boolean = false,
) : MviState {
    private val content: DetailContent? get() = detail?.takeIf { phase == DetailPhase.Ready }

    val subtotalRupiah: Long get() = content?.let { subtotalRupiah(it.categories, form.quantities) } ?: 0L

    /** Subtotal plus ONE service fee per order (ADR-0010); 0 with no tickets. */
    val totalRupiah: Long
        get() = content?.let { orderTotalRupiah(subtotalRupiah, it.adminFeeRupiah, form.totalTickets) } ?: 0L

    /** "Lanjut bayar" is enabled: >=1 ticket, every attendee valid, no sold-out category. */
    val canProceed: Boolean
        get() = content?.let { canProceed(form, it.maxTickets, it.categories) && !isSubmitting } ?: false

    /** True while the countdown should tick. */
    val isCountingDown: Boolean get() = phase == DetailPhase.Ready
}

/** One-time effects of the Detail page. */
sealed interface DetailEffect : MviEffect {
    data object NavigateBack : DetailEffect

    data object NavigateHome : DetailEffect

    /** Queue for the event again after expiry. */
    data class NavigateToQueue(val eventId: String) : DetailEffect

    data class NavigateToPayment(val bookingId: String) : DetailEffect

    data class ShowError(val error: DetailError) : DetailEffect
}

/** Seconds left until [expiresAtMillis] at server time [nowMillis], rounded up, never negative. */
internal fun remainingSeconds(
    expiresAtMillis: Long,
    nowMillis: Long,
): Int {
    val left = expiresAtMillis - nowMillis
    return if (left <= 0) 0 else ((left + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}

private const val MILLIS_PER_SECOND = 1_000L

/** Maps the MVI state onto the render cases. [errorMessage] resolves localized copy. */
fun DetailState.toUiState(errorMessage: (DetailError) -> String): DetailUiState =
    when (val p = phase) {
        DetailPhase.Loading -> DetailUiState.Loading
        DetailPhase.Empty -> DetailUiState.Empty
        is DetailPhase.Failed -> DetailUiState.Error(errorMessage(p.error))
        DetailPhase.Ready ->
            detail?.let {
                DetailUiState.Content(it, remainingSeconds, form, isSubmitting, subtotalRupiah, totalRupiah, canProceed)
            }
                ?: DetailUiState.Loading
        DetailPhase.Expired -> detail?.let { DetailUiState.Expired(it) } ?: DetailUiState.Loading
    }

/** Domain offering -> screen content. Availability derives from stock (0 sold out, few = almost gone). */
internal fun DetailOffering.toDetailContent(): DetailContent =
    DetailContent(
        eventTitle = event.title,
        eventSubtitle = event.subtitle,
        eventMeta = event.meta,
        categories =
            categories.map {
                SeatCategoryUi(
                    id = it.id,
                    name = it.name,
                    description = it.description,
                    priceRupiah = it.priceRupiah,
                    availability = availabilityOf(it.stock),
                    entryGate = it.entryGate,
                )
            },
        accountEmail = profile.email,
        adminFeeRupiah = adminFeeRupiah,
        reservedUnits = reservedUnits,
        stockByCategory = categories.associate { it.id to it.stock },
    )

internal fun availabilityOf(stock: Int): SeatAvailability =
    when {
        stock <= 0 -> SeatAvailability.SoldOut
        stock <= ALMOST_GONE_STOCK -> SeatAvailability.AlmostGone
        else -> SeatAvailability.Available
    }

/** Booking request for the current selection; each holder is paired with its own category. */
internal fun DetailFormState.toBookingRequest(eventId: String): BookingRequest =
    BookingRequest(
        eventId = eventId,
        tickets =
            attendees.map { BookingTicket(it.categoryId, it.name.trim(), it.nik) },
    )
