@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.rusuh.app.ui.components.QueuePhase
import com.rusuh.app.ui.components.QueuePositionCard
import com.rusuh.app.ui.components.QueuePositionUi
import com.rusuh.app.ui.components.accentColors
import com.rusuh.app.ui.theme.RusuhSpacing

/**
 * Waiting Room screen — assembles the [QueuePositionCard] with screen chrome and
 * phase status messaging.
 *
 * Stateless and UI-only: it renders the [state] it is handed and nothing more.
 * There is NO ViewModel, NO repository and NO API here.
 *
 * TODO(M3): wire WaitingRoomViewModel state — collect the MVI state/effects and
 *  map them onto [WaitingRoomUiState], replacing the caller-supplied [state].
 * TODO(M4): feed [QueuePositionUi] from the live queue-position API (FR-07/FR-10).
 *
 * @param state what to render (loading / content / empty / error).
 * @param eventTitle event name shown in the top bar; null shows a generic title.
 * @param onBack optional back-navigation handler; shows a back button when non-null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaitingRoomScreen(
    state: WaitingRoomUiState,
    modifier: Modifier = Modifier,
    eventTitle: String? = null,
    onBack: (() -> Unit)? = null,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = eventTitle ?: "Ruang Tunggu",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        // Text chevron rather than a material-icons dependency for one glyph.
                        TextButton(onClick = onBack) {
                            Text(text = "‹ Kembali")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(RusuhSpacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                WaitingRoomUiState.Loading -> LoadingState()
                WaitingRoomUiState.Empty -> EmptyState()
                is WaitingRoomUiState.Error -> ErrorState(message = state.message)
                is WaitingRoomUiState.Content -> ContentState(position = state.position)
            }
        }
    }
}

/**
 * UI-level render state for the Waiting Room screen.
 *
 * This is NOT the MVI state — it is a presentation-only discriminator so the
 * stateless screen can render each visual placeholder (loading / error / empty
 * / content) that the design calls for. FR-04-M3 introduces the real
 * WaitingRoomViewModel and maps its MVI state onto these cases.
 */
sealed interface WaitingRoomUiState {
    /** Fetching the initial queue position. */
    data object Loading : WaitingRoomUiState

    /** Loaded: an active queue position to display. */
    data class Content(val position: QueuePositionUi) : WaitingRoomUiState

    /** No queue for this event (customer was admitted directly / nothing to show). */
    data object Empty : WaitingRoomUiState

    /**
     * Failed to load or lost the queue connection.
     *
     * @param message pre-formatted, user-facing error copy supplied by the caller.
     */
    data class Error(val message: String) : WaitingRoomUiState
}

@Composable
private fun ContentState(position: QueuePositionUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        QueuePositionCard(data = position)
        Spacer(Modifier.height(RusuhSpacing.xl))
        Text(
            text = position.phase.statusMessage(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = position.phase.accentColors().container,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.semantics { contentDescription = "Memuat posisi antrian" },
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(RusuhSpacing.xl))
        Text(
            text = "Memuat posisi antrian…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyState() {
    CenteredMessage(
        title = "Tidak ada antrian",
        body = "Anda tidak sedang berada dalam antrian untuk acara ini.",
    )
}

@Composable
private fun ErrorState(message: String) {
    CenteredMessage(
        title = "Gagal memuat antrian",
        body = message,
        titleColor = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$title. $body" },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = titleColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Status line shown below the queue card for a phase. Pure function —
 * unit-tested directly.
 *
 * Mirrors the FR-04 copy: waiting (FR-06/07), turn arrived (FR-08), queue
 * closed on sold-out (FR-08/T-03).
 */
fun QueuePhase.statusMessage(): String =
    when (this) {
        QueuePhase.Waiting -> "Menunggu giliran…"
        QueuePhase.YourTurn -> "Giliran Anda tiba"
        QueuePhase.QueueClosed -> "Antrian ditutup — tiket telah habis terjual"
    }
