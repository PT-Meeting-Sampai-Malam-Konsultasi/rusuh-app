@file:Suppress("LongParameterList")

package com.rusuh.app.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.feature.auth.ErrorLine
import com.rusuh.app.feature.auth.SignInActions
import com.rusuh.app.feature.auth.SignInContent
import com.rusuh.app.feature.auth.SignInStatus
import com.rusuh.app.feature.auth.SignInUiState
import com.rusuh.app.feature.auth.SignInVariant
import com.rusuh.app.feature.queues.MyQueuesHeader
import com.rusuh.app.ui.components.BrutalButton
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.account_error_body
import rusuh_app.shared.generated.resources.account_error_title
import rusuh_app.shared.generated.resources.account_loading
import rusuh_app.shared.generated.resources.account_session_expired
import rusuh_app.shared.generated.resources.action_retry

/**
 * Akun tab (AUTH-00-M5 #102, ACCOUNT-00 #104): the normal tab chrome (RUSUH! header with ID|EN, bottom bar with
 * Akun selected) around [AccountContent].
 *
 * @param body what the tab shows (see [AccountBody]); `AccountRoute` derives it from the session.
 * @param signInState the login step shown while signed out.
 * @param signInStatus the in-flight state and the inline error of the login.
 * @param signInActions callbacks of the login steps.
 * @param profileActions callbacks of the signed-in page ("Keluar").
 * @param onRetryProfile retry after the account could not be loaded.
 */
@Composable
fun AccountScreen(
    body: AccountBody,
    signInState: SignInUiState,
    modifier: Modifier = Modifier,
    signInStatus: SignInStatus = SignInStatus(),
    signInActions: SignInActions = SignInActions(),
    profileActions: AccountProfileActions = AccountProfileActions(onSignOut = {}),
    onRetryProfile: () -> Unit = {},
    onTabSelected: (RusuhTab) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhBrandColors.Paper,
        topBar = { MyQueuesHeader() },
        bottomBar = { RusuhBottomBar(selectedTab = RusuhTab.Akun, onTabSelected = onTabSelected) },
    ) { innerPadding ->
        AccountContent(
            body = body,
            signInState = signInState,
            signInStatus = signInStatus,
            signInActions = signInActions,
            profileActions = profileActions,
            onRetryProfile = onRetryProfile,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}

/** Compact login, account page, and loading/error states derived by [AccountRoute]. */
@Composable
fun AccountContent(
    body: AccountBody,
    signInState: SignInUiState,
    modifier: Modifier = Modifier,
    signInStatus: SignInStatus = SignInStatus(),
    signInActions: SignInActions = SignInActions(),
    profileActions: AccountProfileActions = AccountProfileActions(onSignOut = {}),
    onRetryProfile: () -> Unit = {},
) {
    when (body) {
        AccountBody.Restoring, is AccountBody.ProfileLoading ->
            CenteredMessage(stringResource(Res.string.account_loading), modifier)
        is AccountBody.SignedOut ->
            CompactSignIn(
                signInState,
                signInStatus,
                signInActions,
                body.sessionExpired,
                body.analyticsEnabled,
                profileActions.onToggleAnalytics,
                modifier,
            )
        is AccountBody.Profile -> AccountProfileContent(body.profile, profileActions, modifier)
        is AccountBody.ProfileError -> ProfileError(onRetryProfile, modifier)
    }
}

/** Compact login (#102): only what the full screen shows above its first thick rule. */
@Composable
private fun CompactSignIn(
    state: SignInUiState,
    status: SignInStatus,
    actions: SignInActions,
    sessionExpired: Boolean,
    analyticsEnabled: Boolean,
    onToggleAnalytics: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xl)),
    ) {
        if (sessionExpired) {
            SessionExpiredLine()
            Spacer(Modifier.height(RusuhSpacing.xl))
        }
        SignInContent(state = state, actions = actions, variant = SignInVariant.Compact, status = status)
        Spacer(Modifier.height(RusuhSpacing.xxl))
        AnalyticsRow(analyticsEnabled, onToggleAnalytics)
    }
}

/** "Your session ended. Sign in again." above the login after the session expired. */
@Composable
private fun SessionExpiredLine() {
    ErrorLine(stringResource(Res.string.account_session_expired))
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** The account could not be loaded: a short message and one retry button. */
@Composable
private fun ProfileError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(RusuhSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.account_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
        )
        Spacer(Modifier.height(RusuhSpacing.md))
        Text(
            text = stringResource(Res.string.account_error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RusuhSpacing.xl))
        BrutalButton(
            text = stringResource(Res.string.action_retry),
            onClick = onRetry,
            modifier = Modifier.padding(end = RusuhSpacing.shadowMd, bottom = RusuhSpacing.shadowMd),
        )
    }
}
