@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.components.QueueBigNumber
import com.rusuh.app.ui.components.QueuePositionCard
import com.rusuh.app.ui.components.formatCountdown
import com.rusuh.app.ui.components.formatQueueNumber
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.rusuhDisplayFontFamily
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_back_description
import rusuh_app.shared.generated.resources.waiting_empty_body
import rusuh_app.shared.generated.resources.waiting_empty_title
import rusuh_app.shared.generated.resources.waiting_error_title
import rusuh_app.shared.generated.resources.waiting_missed_body
import rusuh_app.shared.generated.resources.waiting_missed_cta
import rusuh_app.shared.generated.resources.waiting_missed_estimate
import rusuh_app.shared.generated.resources.waiting_missed_lead
import rusuh_app.shared.generated.resources.waiting_missed_title
import rusuh_app.shared.generated.resources.waiting_paused_hint
import rusuh_app.shared.generated.resources.waiting_paused_lead
import rusuh_app.shared.generated.resources.waiting_paused_title
import rusuh_app.shared.generated.resources.waiting_prequeue_body
import rusuh_app.shared.generated.resources.waiting_prequeue_hint
import rusuh_app.shared.generated.resources.waiting_prequeue_lead
import rusuh_app.shared.generated.resources.waiting_prequeue_title
import rusuh_app.shared.generated.resources.waiting_sold_out_body
import rusuh_app.shared.generated.resources.waiting_sold_out_cta
import rusuh_app.shared.generated.resources.waiting_sold_out_lead
import rusuh_app.shared.generated.resources.waiting_sold_out_notify
import rusuh_app.shared.generated.resources.waiting_sold_out_title
import rusuh_app.shared.generated.resources.waiting_title
import rusuh_app.shared.generated.resources.waiting_turn_cta
import rusuh_app.shared.generated.resources.waiting_turn_hint
import rusuh_app.shared.generated.resources.waiting_turn_lead
import rusuh_app.shared.generated.resources.waiting_turn_title

/**
 * Waiting Room screen (FR-04-M2). Pure `state -> UI`; interactions go out through [dispatch].
 *
 * State and effects come from [WaitingRoomViewModel] via [WaitingRoomRoute] (FR-04-M3).
 * No ViewModel, repository or API here.
 */
@Composable
fun WaitingRoomScreen(
    state: WaitingRoomUiState,
    dispatch: (WaitingRoomAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(RusuhColors.Paper).statusBarsPadding()) {
        WaitingTopBar(onBack = { dispatch(WaitingRoomAction.Back) })
        Box(Modifier.fillMaxWidth().height(RusuhSpacing.xxs).background(RusuhColors.Ink))
        Column(
            modifier = Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state) {
                WaitingRoomUiState.Loading -> CenteredBox { LoadingContent() }
                WaitingRoomUiState.Empty ->
                    CenteredBox {
                        CenteredMessage(
                            title = stringResource(Res.string.waiting_empty_title),
                            body = stringResource(Res.string.waiting_empty_body),
                        )
                    }
                is WaitingRoomUiState.Error ->
                    CenteredBox {
                        CenteredMessage(
                            title = stringResource(Res.string.waiting_error_title),
                            body = state.message,
                            titleColor = RusuhColors.Pink,
                        )
                    }
                is WaitingRoomUiState.Active -> ActiveContent(state, dispatch)
            }
        }
    }
}

@Composable
private fun WaitingTopBar(onBack: () -> Unit) {
    val backDescription = stringResource(Res.string.waiting_back_description)
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = TOP_BAR_HEIGHT), contentAlignment = Alignment.Center) {
        Text(
            text = BACK_ARROW,
            style = MaterialTheme.typography.headlineMedium,
            color = RusuhColors.Ink,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDescription }
                    .padding(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.lg),
        )
        Text(
            text = stringResource(Res.string.waiting_title),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = rusuhDisplayFontFamily()),
            fontWeight = FontWeight.Bold,
            color = RusuhColors.Ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun ActiveContent(
    state: WaitingRoomUiState.Active,
    dispatch: (WaitingRoomAction) -> Unit,
) {
    val phase = state.phase
    Column(Modifier.fillMaxWidth().padding(horizontal = RusuhSpacing.xl)) {
        if (state.isGuest && phase.showsGuestBanner()) {
            Spacer(Modifier.height(RusuhSpacing.lg))
            GuestBanner(onSignIn = { dispatch(WaitingRoomAction.SignIn) })
        }
        Spacer(Modifier.height(RusuhSpacing.xl))
        EventPoster(event = state.event, phase = phase)
        Spacer(Modifier.height(RusuhSpacing.xxl))
        PhaseContent(event = state.event, phase = phase, dispatch = dispatch)
        if (phase.showsNotes()) {
            Spacer(Modifier.height(RusuhSpacing.xxl))
            WaitingNotes(onLeave = { dispatch(WaitingRoomAction.LeaveQueue) })
        }
        Spacer(Modifier.height(RusuhSpacing.xxl))
    }
}

@Composable
private fun PhaseContent(
    event: WaitingRoomEventUi,
    phase: WaitingRoomPhase,
    dispatch: (WaitingRoomAction) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        when (phase) {
            is WaitingRoomPhase.PreQueue -> PreQueueContent(phase)
            is WaitingRoomPhase.Waiting -> QueuePositionCard(data = phase.queue)
            is WaitingRoomPhase.Paused -> PausedContent(phase)
            is WaitingRoomPhase.YourTurn -> YourTurnContent(phase, onEnter = { dispatch(WaitingRoomAction.EnterNow) })
            is WaitingRoomPhase.MissedTurn ->
                MissedContent(
                    phase,
                    onQueueAgain = { dispatch(WaitingRoomAction.QueueAgain) },
                )
            WaitingRoomPhase.SoldOut -> SoldOutContent(event, dispatch)
        }
    }
}

@Composable
private fun PreQueueContent(phase: WaitingRoomPhase.PreQueue) {
    Lead(stringResource(Res.string.waiting_prequeue_lead))
    Spacer(Modifier.height(RusuhSpacing.md))
    QueueBigNumber(text = formatCountdown(phase.secondsUntilOpen), ink = true)
    Spacer(Modifier.height(RusuhSpacing.xl))
    Headline(stringResource(Res.string.waiting_prequeue_title), size = HeadlineSize.Medium)
    Spacer(Modifier.height(RusuhSpacing.xs))
    Muted(stringResource(Res.string.waiting_prequeue_body, phase.opensAtLabel))
    Spacer(Modifier.height(RusuhSpacing.xxl))
    Body(stringResource(Res.string.waiting_prequeue_hint))
}

@Composable
private fun PausedContent(phase: WaitingRoomPhase.Paused) {
    Lead(stringResource(Res.string.waiting_paused_lead))
    Spacer(Modifier.height(RusuhSpacing.md))
    QueueBigNumber(text = formatQueueNumber(phase.position), paused = true)
    Spacer(Modifier.height(RusuhSpacing.xl))
    Headline(stringResource(Res.string.waiting_paused_title), size = HeadlineSize.Medium)
    Spacer(Modifier.height(RusuhSpacing.xs))
    Muted(stringResource(phase.reason.messageRes()))
    Spacer(Modifier.height(RusuhSpacing.xxl))
    Body(stringResource(Res.string.waiting_paused_hint))
}

@Composable
private fun YourTurnContent(
    phase: WaitingRoomPhase.YourTurn,
    onEnter: () -> Unit,
) {
    Lead(stringResource(Res.string.waiting_turn_lead, formatQueueNumber(phase.myNumber)))
    Spacer(Modifier.height(RusuhSpacing.lg))
    Headline(stringResource(Res.string.waiting_turn_title), color = RusuhColors.Pink, shadow = RusuhColors.Blue)
    Spacer(Modifier.height(RusuhSpacing.xxl))
    PrimaryButton(text = stringResource(Res.string.waiting_turn_cta), onClick = onEnter)
    Spacer(Modifier.height(RusuhSpacing.xl))
    TurnTimer(seconds = phase.secondsLeft)
    Spacer(Modifier.height(RusuhSpacing.xl))
    Muted(stringResource(Res.string.waiting_turn_hint))
}

@Composable
private fun MissedContent(
    phase: WaitingRoomPhase.MissedTurn,
    onQueueAgain: () -> Unit,
) {
    Lead(stringResource(Res.string.waiting_missed_lead))
    Spacer(Modifier.height(RusuhSpacing.lg))
    Headline(stringResource(Res.string.waiting_missed_title))
    Spacer(Modifier.height(RusuhSpacing.xl))
    Body(stringResource(Res.string.waiting_missed_body))
    Spacer(Modifier.height(RusuhSpacing.xxl))
    PrimaryButton(text = stringResource(Res.string.waiting_missed_cta), onClick = onQueueAgain)
    phase.estimateMinutes?.let { minutes ->
        Spacer(Modifier.height(RusuhSpacing.xl))
        Muted(stringResource(Res.string.waiting_missed_estimate, minutes))
    }
}

@Composable
private fun SoldOutContent(
    event: WaitingRoomEventUi,
    dispatch: (WaitingRoomAction) -> Unit,
) {
    Lead(stringResource(Res.string.waiting_sold_out_lead, event.title))
    Spacer(Modifier.height(RusuhSpacing.lg))
    Headline(stringResource(Res.string.waiting_sold_out_title))
    Spacer(Modifier.height(RusuhSpacing.xl))
    Body(stringResource(Res.string.waiting_sold_out_body))
    Spacer(Modifier.height(RusuhSpacing.xxl))
    PrimaryButton(
        text = stringResource(Res.string.waiting_sold_out_cta),
        onClick = { dispatch(WaitingRoomAction.SeeOtherEvents) },
    )
    Spacer(Modifier.height(RusuhSpacing.xl))
    TextLink(
        text = stringResource(Res.string.waiting_sold_out_notify),
        onClick = { dispatch(WaitingRoomAction.NotifyMe) },
    )
}

private const val BACK_ARROW = "←"
private val TOP_BAR_HEIGHT = 56.dp
