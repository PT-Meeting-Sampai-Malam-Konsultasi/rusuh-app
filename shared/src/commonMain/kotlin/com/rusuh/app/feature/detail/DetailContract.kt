@file:Suppress("TooManyFunctions")

package com.rusuh.app.feature.detail

import androidx.compose.runtime.Immutable
import com.rusuh.app.domain.model.AttendeeValidation
import com.rusuh.app.domain.model.NikRules
import com.rusuh.app.domain.model.isValid
import com.rusuh.app.domain.model.validateAttendeeLocally
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.ui.components.SeatCategoryUi

/** Hard cap on tickets in one order (ADR-0004 / FR-13). */
const val MAX_TICKETS_PER_ORDER = 3

/** Required NIK length: 16 digits as printed on a KTP (FR-13). */
const val NIK_LENGTH = NikRules.LENGTH

/** Length of the order window in seconds (server-driven in the real app; 5 minutes). */
const val ORDER_WINDOW_SECONDS = 5 * 60

/**
 * Loaded content for the Detail Ticket page, mapped from the domain offering by the ViewModel
 * (placeholder data behind the stub repository until the APIs land).
 *
 * @param eventTitle main event name (e.g. "Samudra").
 * @param eventSubtitle tagline under the title; null hides it.
 * @param eventMeta pre-formatted date · time · venue line, already localized.
 * @param categories the seat categories offered for this event (`getCategories`).
 * @param accountEmail read-only e-ticket destination (the account email).
 * @param adminFeeRupiah "Biaya layanan": ONE fixed fee per ORDER, not per ticket (ADR-0010).
 * @param reservedUnits units the queue admission reserved; the order is capped at
 *   min([MAX_TICKETS_PER_ORDER], [reservedUnits]).
 * @param stockByCategory remaining stock per category id; a missing id means no stock limit.
 */
@Immutable
data class DetailContent(
    val eventTitle: String,
    val eventSubtitle: String?,
    val eventMeta: String,
    val categories: List<SeatCategoryUi>,
    val accountEmail: String,
    val adminFeeRupiah: Long,
    val reservedUnits: Int = MAX_TICKETS_PER_ORDER,
    val stockByCategory: Map<String, Int> = emptyMap(),
) {
    /** Maximum tickets this order may hold. */
    val maxTickets: Int get() = minOf(MAX_TICKETS_PER_ORDER, reservedUnits)

    /** Remaining stock of [categoryId] (unbounded when unknown). */
    fun stockOf(categoryId: String): Int = stockByCategory[categoryId] ?: Int.MAX_VALUE
}

/**
 * UI-level render state for the Detail Ticket page. NOT the MVI state: [DetailState] is mapped
 * onto these cases by [DetailState.toUiState].
 */
sealed interface DetailUiState {
    /** Fetching the event + categories. */
    data object Loading : DetailUiState

    /**
     * Loaded.
     *
     * @param remainingSeconds order countdown shown in the top bar.
     * @param form selected quantities and attendee forms (owned by the ViewModel).
     * @param isSubmitting true while "Lanjut bayar" is validating/booking; disables it.
     * @param subtotalRupiah ticket subtotal; [totalRupiah] adds ONE service fee; [canProceed] gates
     *   "Lanjut bayar". All three are computed by [DetailState].
     */
    @Immutable
    data class Content(
        val detail: DetailContent,
        val remainingSeconds: Int = ORDER_WINDOW_SECONDS,
        val form: DetailFormState = DetailFormState(),
        val isSubmitting: Boolean = false,
        val subtotalRupiah: Long = 0L,
        val totalRupiah: Long = 0L,
        val canProceed: Boolean = false,
    ) : DetailUiState

    /** No categories to sell. */
    data object Empty : DetailUiState

    /** Failed to load; [message] is pre-formatted, user-facing copy. */
    data class Error(val message: String) : DetailUiState

    /** The order window elapsed: the page is dimmed behind the "Waktu habis" sheet. */
    @Immutable
    data class Expired(val detail: DetailContent) : DetailUiState
}

/** User intents emitted by the stateless [DetailScreen] and handled by [DetailViewModel]. */
sealed interface DetailIntent : MviIntent {
    /** Initial load of the event and categories. */
    data object Load : DetailIntent

    /** Reload after a failed load. */
    data object Retry : DetailIntent

    data object Back : DetailIntent

    /** Validate every attendee, create the booking and continue to payment. */
    data object Proceed : DetailIntent

    /** Change the e-ticket email (opens account settings later). */
    data object ChangeEmail : DetailIntent

    /** Queue again after the order window expired. */
    data object QueueAgain : DetailIntent

    data object GoHome : DetailIntent

    /** One more ticket of [categoryId] (capped by the order cap and the category stock). */
    data class IncrementCategory(val categoryId: String) : DetailIntent

    data class DecrementCategory(val categoryId: String) : DetailIntent

    data class AttendeeNameChanged(val index: Int, val name: String) : DetailIntent

    data class AttendeeNikChanged(val index: Int, val nik: String) : DetailIntent

    /** Retry failed validation for an attendee. */
    data class RetryValidation(val index: Int) : DetailIntent

    /** The countdown ticked: recompute remaining time from the server clock. */
    data object Tick : DetailIntent
}

/**
 * One attendee slot (one per ticket). [nik] holds digits only; the screen groups them for display.
 *
 * @param id stable ticket identity, unique within the order; UI state is keyed by it.
 * @param categoryId the category this ticket belongs to; the booking pairs the holder with it.
 * @param validationStatus inline validation status for this attendee's NIK/name.
 */
@Immutable
data class AttendeeForm(
    val id: Long = 0L,
    val categoryId: String = "",
    val name: String = "",
    val nik: String = "",
    val validationStatus: AttendeeValidationStatus = AttendeeValidationStatus.None,
) {
    override fun toString(): String =
        "AttendeeForm(id=$id, categoryId=$categoryId, name=<redacted>, nik=<redacted>, status=$validationStatus)"
}

/** Inline validation status shown under each attendee field. */
sealed interface AttendeeValidationStatus {
    /** No validation attempt yet (field empty or being typed). */
    data object None : AttendeeValidationStatus

    /** Server validation in progress. */
    data object Checking : AttendeeValidationStatus

    /** Server validation passed. Token is stored separately in the ViewModel. */
    data object Valid : AttendeeValidationStatus

    /** Server rejected the NIK (invalid format, seat taken, or duplicate). */
    data class Rejected(val reason: RejectedReason) : AttendeeValidationStatus

    /** The check could not be done; [reason] tells the user why. They can retry. */
    data class Unavailable(val reason: UnavailableReason = UnavailableReason.SERVER) : AttendeeValidationStatus
}

/** Why an attendee check could not be completed. */
enum class UnavailableReason {
    NETWORK,
    SERVICE,
    TOO_MANY_ATTEMPTS,
    SERVER,
}

/** Reason why the server rejected an attendee's NIK. */
enum class RejectedReason {
    NIK_INVALID,
    SEAT_TAKEN,
    DUPLICATE,
}

/** Maps server validation result to the UI rejection reason. */
internal fun AttendeeValidation.toRejectedReason(): RejectedReason? =
    when (this) {
        is AttendeeValidation.NikInvalid -> RejectedReason.NIK_INVALID
        is AttendeeValidation.NikSeatTaken -> RejectedReason.SEAT_TAKEN
        is AttendeeValidation.ServerDuplicateNik -> RejectedReason.DUPLICATE
        else -> null
    }

/**
 * Form state: quantities per category plus one [AttendeeForm] per ticket. "Tiket N" is the
 * position in [attendees], i.e. insertion order; it renumbers when a middle ticket is removed
 * (the typed data stays with its person).
 */
@Immutable
data class DetailFormState(
    val quantities: Map<String, Int> = emptyMap(),
    val attendees: List<AttendeeForm> = emptyList(),
    val nextTicketId: Long = 1L,
) {
    val totalTickets: Int get() = quantities.values.sum()
}

// --- Pure presentation/form logic (unit-tested in commonTest, no Compose harness) ---

/** True once the order is at (or past) its cap. */
internal fun isCapReached(
    selectedTotal: Int,
    maxTickets: Int = MAX_TICKETS_PER_ORDER,
): Boolean = selectedTotal >= maxTickets

/** Visual-only NIK validity: exactly [NIK_LENGTH] digits. Real validation is `validateAttendee`. */
internal fun isNikValid(nik: String): Boolean = NikRules.isValid(nik)

/** ASCII 0-9 only: `Char.isDigit` also accepts Arabic-Indic and other Unicode digits. */
internal fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

/**
 * Sets a category quantity, clamped to 0..min(remaining cap, [stock]), and reconciles the
 * attendees of THAT category only.
 */
internal fun DetailFormState.withQuantity(
    categoryId: String,
    quantity: Int,
    maxTickets: Int,
    stock: Int = Int.MAX_VALUE,
): DetailFormState {
    val others = quantities.filterKeys { it != categoryId }.values.sum()
    val clamped = quantity.coerceIn(0, (maxTickets - others).coerceAtLeast(0).coerceAtMost(stock.coerceAtLeast(0)))
    val next = quantities + (categoryId to clamped)
    val current = attendees.count { it.categoryId == categoryId }
    return when {
        clamped == current -> copy(quantities = next)
        clamped > current -> {
            // Growing appends new tickets at the end; existing tickets never move.
            val added = List(clamped - current) { AttendeeForm(id = nextTicketId + it, categoryId = categoryId) }
            copy(quantities = next, attendees = attendees + added, nextTicketId = nextTicketId + added.size)
        }
        else -> {
            // Shrinking removes the LAST tickets of this category only.
            val drop =
                attendees.filter { it.categoryId == categoryId }.takeLast(
                    current - clamped,
                ).map { it.id }.toSet()
            copy(quantities = next, attendees = attendees.filterNot { it.id in drop })
        }
    }
}

internal fun DetailFormState.withName(
    index: Int,
    name: String,
): DetailFormState = updateAttendee(index) { it.copy(name = name, validationStatus = AttendeeValidationStatus.None) }

/** Keeps digits only, capped at [NIK_LENGTH]. Resets validation status. */
internal fun DetailFormState.withNik(
    index: Int,
    raw: String,
): DetailFormState =
    updateAttendee(index) {
        it.copy(nik = raw.filter(::isAsciiDigit).take(NIK_LENGTH), validationStatus = AttendeeValidationStatus.None)
    }

/** Updates the validation status of an attendee without touching name/nik. */
internal fun DetailFormState.withValidationStatus(
    index: Int,
    status: AttendeeValidationStatus,
): DetailFormState = updateAttendee(index) { it.copy(validationStatus = status) }

private fun DetailFormState.updateAttendee(
    index: Int,
    transform: (AttendeeForm) -> AttendeeForm,
): DetailFormState =
    if (index !in attendees.indices) {
        this
    } else {
        copy(attendees = attendees.mapIndexed { i, a -> if (i == index) transform(a) else a })
    }

/**
 * 1-based number of an EARLIER ticket that already uses the same complete NIK as ticket [index],
 * or null. One NIK may hold only one ticket per order.
 */
internal fun DetailFormState.duplicateOf(index: Int): Int? {
    val nik = attendees.getOrNull(index)?.nik?.takeIf(::isNikValid) ?: return null
    val earlier = attendees.take(index).indexOfFirst { it.nik == nik }
    return if (earlier >= 0) earlier + 1 else null
}

/**
 * Whether "Lanjut bayar" is enabled: 1..cap tickets, no selected category sold out, every attendee
 * named, NIK valid and unique, and either already validated by the server or currently checking.
 */
internal fun canProceed(
    form: DetailFormState,
    maxTickets: Int,
    categories: List<SeatCategoryUi> = emptyList(),
): Boolean =
    form.totalTickets in 1..maxTickets &&
        categories.none { it.isSoldOut && (form.quantities[it.id] ?: 0) > 0 } &&
        form.attendees.size == form.totalTickets &&
        form.attendees.indices.all { i ->
            val a = form.attendees[i]
            val locallyValid = validateAttendeeLocally(a.name, a.nik, form.attendees.take(i).map { it.nik }).isValid
            val serverOk =
                a.validationStatus is AttendeeValidationStatus.Valid ||
                    a.validationStatus is AttendeeValidationStatus.Checking
            locallyValid && serverOk
        }

/** Running subtotal of selected categories in whole rupiah. */
internal fun subtotalRupiah(
    categories: List<SeatCategoryUi>,
    quantities: Map<String, Int>,
): Long = categories.sumOf { it.priceRupiah * (quantities[it.id] ?: 0) }

/** Order total: ticket subtotal plus ONE admin fee per order (0 when nothing is selected). */
internal fun orderTotalRupiah(
    subtotal: Long,
    adminFee: Long,
    tickets: Int,
): Long = if (tickets > 0) subtotal + adminFee else 0L

/** Formats seconds as "m:ss" for the countdown chip. */
internal fun formatCountdown(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Seconds at/below which the countdown chip switches to the warning treatment. */
internal const val COUNTDOWN_WARN_SECONDS = 60

/** Groups NIK digits in blocks of four ("3174 0552 0396 0004"). */
internal fun groupNik(digits: String): String = digits.chunked(4).joinToString(" ")

/** Mask glyph for hidden NIK digits. */
internal const val NIK_MASK_CHAR = '\u2022'

/** Digits left visible when the NIK is masked. */
internal const val NIK_VISIBLE_TAIL = 4

/**
 * NIK as displayed: grouped 4-4-4-4; when [masked] every digit but the last [NIK_VISIBLE_TAIL]
 * becomes a bullet ("•••• •••• •••• 1234"). Same length as [groupNik], so offsets map identically.
 */
internal fun nikDisplay(
    digits: String,
    masked: Boolean,
): String {
    val shown =
        if (masked) {
            val hidden = (digits.length - NIK_VISIBLE_TAIL).coerceAtLeast(0)
            NIK_MASK_CHAR.toString().repeat(hidden) + digits.substring(hidden)
        } else {
            digits
        }
    return groupNik(shown)
}

/** Raw-digit offset to grouped-text offset (a space follows every full block of four). */
internal fun nikOriginalToTransformed(offset: Int): Int = offset + (offset - 1).coerceAtLeast(0) / 4

/** Grouped-text offset back to raw-digit offset, clamped to [length]. */
internal fun nikTransformedToOriginal(
    offset: Int,
    length: Int,
): Int = (offset - offset / 5).coerceAtMost(length)
