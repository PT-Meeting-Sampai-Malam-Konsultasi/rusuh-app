@file:Suppress("TooManyFunctions")

package com.rusuh.app.feature.detail

import androidx.compose.runtime.Immutable
import com.rusuh.app.ui.components.SeatCategoryUi

/** Hard cap on tickets in one order (ADR-0004 / FR-13). */
const val MAX_TICKETS_PER_ORDER = 3

/** Required NIK length: 16 digits as printed on a KTP (FR-13). */
const val NIK_LENGTH = 16

/** Length of the order window in seconds (server-driven in the real app; 5 minutes). */
const val ORDER_WINDOW_SECONDS = 5 * 60

/**
 * Static placeholder content for the Detail Ticket page (FR-05 M1+M2). UI-only sample data:
 * there is NO ViewModel, repository or API behind it (M3/M4 replace it).
 *
 * @param eventTitle main event name (e.g. "Samudra").
 * @param eventSubtitle tagline under the title; null hides it.
 * @param eventMeta pre-formatted date · time · venue line, already localized.
 * @param categories the seat categories offered for this event (`getCategories`).
 * @param accountName the account's name; "Saya sendiri" prefills the attendee name with it.
 *   The account has NO NIK, so the NIK is always typed.
 * @param accountEmail read-only e-ticket destination (the account email).
 * @param adminFeeRupiah "Biaya layanan": ONE fixed fee per ORDER, not per ticket (ADR-0010).
 * @param reservedUnits units the queue admission reserved; the order is capped at
 *   min([MAX_TICKETS_PER_ORDER], [reservedUnits]).
 */
@Immutable
data class DetailContent(
    val eventTitle: String,
    val eventSubtitle: String?,
    val eventMeta: String,
    val categories: List<SeatCategoryUi>,
    val accountName: String,
    val accountEmail: String,
    val adminFeeRupiah: Int,
    val reservedUnits: Int = MAX_TICKETS_PER_ORDER,
) {
    /** Maximum tickets this order may hold. */
    val maxTickets: Int get() = minOf(MAX_TICKETS_PER_ORDER, reservedUnits)
}

/**
 * UI-level render state for the Detail Ticket page. NOT the MVI state: FR-05-M3 introduces the
 * real DetailViewModel and maps its state onto these cases.
 */
sealed interface DetailUiState {
    /** Fetching the event + categories. */
    data object Loading : DetailUiState

    /**
     * Loaded.
     *
     * @param remainingSeconds order countdown shown in the top bar (server-driven in the real
     *   app; ticked by the placeholder host here).
     */
    @Immutable
    data class Content(
        val detail: DetailContent,
        val remainingSeconds: Int = ORDER_WINDOW_SECONDS,
    ) : DetailUiState

    /** No categories to sell. */
    data object Empty : DetailUiState

    /** Failed to load; [message] is pre-formatted, user-facing copy. */
    data class Error(val message: String) : DetailUiState

    /** The order window elapsed: the page is dimmed behind the "Waktu habis" sheet. */
    @Immutable
    data class Expired(val detail: DetailContent) : DetailUiState
}

/** User intents emitted by the stateless [DetailScreen]. */
sealed interface DetailIntent {
    data object Back : DetailIntent

    /** Proceed to payment. TODO(M3/M4): carry the validated selection + attendees. */
    data object Proceed : DetailIntent

    /** Change the e-ticket email (opens account settings later). */
    data object ChangeEmail : DetailIntent

    data object QueueAgain : DetailIntent

    data object GoHome : DetailIntent
}

/** Who a ticket is for. */
enum class AttendeeKind { Self, Other }

/**
 * One attendee slot (one per ticket). [nik] holds digits only; the screen groups them for display.
 */
@Immutable
data class AttendeeForm(
    val kind: AttendeeKind = AttendeeKind.Other,
    val name: String = "",
    val nik: String = "",
)

/** UI-only local form state: quantities per category plus one [AttendeeForm] per ticket. */
@Immutable
data class DetailFormState(
    val quantities: Map<String, Int> = emptyMap(),
    val attendees: List<AttendeeForm> = emptyList(),
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
internal fun isNikValid(nik: String): Boolean = nik.length == NIK_LENGTH && nik.all(::isAsciiDigit)

/** ASCII 0-9 only: `Char.isDigit` also accepts Arabic-Indic and other Unicode digits. */
internal fun isAsciiDigit(c: Char): Boolean = c in '0'..'9'

/** Sets a category quantity, clamped to 0..(remaining cap), and resizes the attendee slots. */
internal fun DetailFormState.withQuantity(
    categoryId: String,
    quantity: Int,
    maxTickets: Int,
): DetailFormState {
    val others = quantities.filterKeys { it != categoryId }.values.sum()
    val clamped = quantity.coerceIn(0, (maxTickets - others).coerceAtLeast(0))
    val next = quantities + (categoryId to clamped)
    val total = next.values.sum()
    // shortcut: slots are positional, so shrinking drops the LAST attendee rather than the one
    // tied to the removed ticket; revisit when M3 keys attendees per ticket in the ViewModel.
    val resized =
        when {
            total == attendees.size -> attendees
            total < attendees.size -> attendees.take(total)
            else -> attendees + List(total - attendees.size) { AttendeeForm() }
        }
    return copy(quantities = next, attendees = resized)
}

/** Switches ticket [index] between self/other. "Saya sendiri" prefills ONLY the name. */
internal fun DetailFormState.withKind(
    index: Int,
    kind: AttendeeKind,
    accountName: String,
): DetailFormState =
    updateAttendee(index) { current ->
        when {
            kind == current.kind -> current
            kind == AttendeeKind.Self -> current.copy(kind = kind, name = accountName)
            else -> current.copy(kind = kind, name = if (current.name == accountName) "" else current.name)
        }
    }

internal fun DetailFormState.withName(
    index: Int,
    name: String,
): DetailFormState = updateAttendee(index) { it.copy(name = name) }

/** Keeps digits only, capped at [NIK_LENGTH]. */
internal fun DetailFormState.withNik(
    index: Int,
    raw: String,
): DetailFormState = updateAttendee(index) { it.copy(nik = raw.filter(::isAsciiDigit).take(NIK_LENGTH)) }

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

/** Categories expanded to one entry per selected ticket, in list order ("Tiket 1 · Festival B"). */
internal fun ticketCategories(
    categories: List<SeatCategoryUi>,
    quantities: Map<String, Int>,
): List<SeatCategoryUi> = categories.flatMap { c -> List(quantities[c.id] ?: 0) { c } }

/**
 * Whether "Lanjut bayar" is enabled: 1..cap tickets, no selected category sold out, every attendee
 * named, NIK valid and unique.
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
            a.name.isNotBlank() && isNikValid(a.nik) && form.duplicateOf(i) == null
        }

/** Running subtotal of selected categories in whole rupiah. */
internal fun subtotalRupiah(
    categories: List<SeatCategoryUi>,
    quantities: Map<String, Int>,
): Int = categories.sumOf { it.priceRupiah * (quantities[it.id] ?: 0) }

/** Order total: ticket subtotal plus ONE admin fee per order (0 when nothing is selected). */
internal fun orderTotalRupiah(
    subtotal: Int,
    adminFee: Int,
    tickets: Int,
): Int = if (tickets > 0) subtotal + adminFee else 0

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
