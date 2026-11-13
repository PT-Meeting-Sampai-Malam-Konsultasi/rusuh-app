@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.runtime.Immutable
import com.rusuh.app.ui.components.QueuePositionUi
import org.jetbrains.compose.resources.StringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_paused_reason_operator
import rusuh_app.shared.generated.resources.waiting_paused_reason_stock
import rusuh_app.shared.generated.resources.waiting_poster_closed
import rusuh_app.shared.generated.resources.waiting_poster_live
import rusuh_app.shared.generated.resources.waiting_poster_paused
import rusuh_app.shared.generated.resources.waiting_poster_prequeue

/** Event shown on the poster card, identical in every phase. */
@Immutable
data class WaitingRoomEventUi(
    val title: String,
    val subtitle: String,
    val dateTimeLine: String,
    val venueLine: String,
)

/** Why a queue is paused (`Frontier` pause reasons). */
enum class PauseReason {
    /** Stock temporarily full ("Penuh sementara"). */
    Stock,

    /** Operator is checking the system ("Penyelenggara sedang memeriksa sistem"). */
    Operator,
}

/** The six Figma states of the Waiting Room (Pre-queue, Waiting, Paused, Your turn, Missed, Sold out). */
@Immutable
sealed interface WaitingRoomPhase {
    /** Before the queue opens; [secondsUntilOpen] drives the countdown, [opensAtLabel] is e.g. "10.00 WIB". */
    data class PreQueue(val secondsUntilOpen: Int, val opensAtLabel: String) : WaitingRoomPhase

    /** In line and moving. */
    data class Waiting(val queue: QueuePositionUi) : WaitingRoomPhase

    /** Queue halted; the place is kept. */
    data class Paused(val position: Int, val reason: PauseReason) : WaitingRoomPhase

    /** The customer's turn: [secondsLeft] of the 60 s window to enter. */
    data class YourTurn(val myNumber: Int, val secondsLeft: Int, val claiming: Boolean = false) : WaitingRoomPhase

    /** The 60 s window ran out. */
    data class MissedTurn(val estimateMinutes: Int?) : WaitingRoomPhase

    /** Tickets sold out; the queue is dissolved. */
    data object SoldOut : WaitingRoomPhase
}

/** Why the Waiting Room is in its error state. */
enum class WaitingRoomErrorKind {
    Generic,

    /** Session lost mid-queue (401/SignInRequiredException during polling). Opens sign-in to resume. */
    SignInRequired,
}

/** What the screen renders. */
@Immutable
sealed interface WaitingRoomUiState {
    /** Fetching the initial queue position. */
    data object Loading : WaitingRoomUiState

    /** No queue for this event. */
    data object Empty : WaitingRoomUiState

    /** Failed to load; [message] is pre-formatted caller copy, [kind] says what failed (the visuals are the same). */
    data class Error(
        val message: String,
        val kind: WaitingRoomErrorKind = WaitingRoomErrorKind.Generic,
    ) : WaitingRoomUiState

    /** A queue exists: [event] poster + [phase]. */
    data class Active(
        val event: WaitingRoomEventUi,
        val phase: WaitingRoomPhase,
    ) : WaitingRoomUiState
}

/** User actions raised by the screen; the caller (future ViewModel) decides what they do. */
sealed interface WaitingRoomAction {
    data object Back : WaitingRoomAction

    data object EnterNow : WaitingRoomAction

    data object QueueAgain : WaitingRoomAction

    data object LeaveQueue : WaitingRoomAction

    data object SeeOtherEvents : WaitingRoomAction

    data object NotifyMe : WaitingRoomAction

    /** Reload after a failed load (Error state). */
    data object Retry : WaitingRoomAction
}

/** Poster tag resource for a phase (LIVE / opens at / paused / closed). */
fun WaitingRoomPhase.posterTag(): StringResource =
    when (this) {
        is WaitingRoomPhase.PreQueue -> Res.string.waiting_poster_prequeue
        is WaitingRoomPhase.Paused -> Res.string.waiting_poster_paused
        WaitingRoomPhase.SoldOut -> Res.string.waiting_poster_closed
        is WaitingRoomPhase.Waiting, is WaitingRoomPhase.YourTurn, is WaitingRoomPhase.MissedTurn ->
            Res.string.waiting_poster_live
    }

/** Pause explanation resource for a [PauseReason]. */
fun PauseReason.messageRes(): StringResource =
    when (this) {
        PauseReason.Stock -> Res.string.waiting_paused_reason_stock
        PauseReason.Operator -> Res.string.waiting_paused_reason_operator
    }

/** The "Posisi Anda aman ..." notes + leave link show while the customer is still in line. */
fun WaitingRoomPhase.showsNotes(): Boolean =
    when (this) {
        is WaitingRoomPhase.PreQueue, is WaitingRoomPhase.Waiting, is WaitingRoomPhase.Paused -> true
        is WaitingRoomPhase.YourTurn, is WaitingRoomPhase.MissedTurn, WaitingRoomPhase.SoldOut -> false
    }
