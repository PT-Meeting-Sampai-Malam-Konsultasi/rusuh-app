package com.rusuh.app.feature.account

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.rusuh.app.domain.session.SessionPhase
import com.rusuh.app.feature.auth.SignInViewModel
import com.rusuh.app.feature.auth.signInActions
import com.rusuh.app.ui.components.RusuhTab

/**
 * Stateful host of the Akun tab: the session decides what shows (login, loading, account page, retry). The login
 * itself is [signInViewModel]; while its new-account step is open the login stays on screen even though the session
 * already exists.
 */
@Composable
fun AccountRoute(
    accountViewModel: AccountViewModel,
    signInViewModel: SignInViewModel,
    onTabSelected: (RusuhTab) -> Unit,
) {
    val account by accountViewModel.state.collectAsState()
    val signIn by signInViewModel.state.collectAsState()

    val canShowProfile = account.phase == SessionPhase.SignedIn && !signIn.holdsAccountPage
    // Idle again after a sign-out or an expiry: load when the page may be shown (also the first time).
    LaunchedEffect(canShowProfile, account.profile) {
        if (canShowProfile && account.profile == ProfileLoad.Idle) accountViewModel.dispatch(AccountIntent.LoadProfile)
    }
    LaunchedEffect(signIn.holdsAccountPage) {
        accountViewModel.dispatch(AccountIntent.NameStepVisibilityChanged(signIn.holdsAccountPage))
    }
    // Signing in on this tab needs no navigation (the session shows the page); drain so nothing piles up.
    LaunchedEffect(signInViewModel) { signInViewModel.effect.collect { } }

    val actions = remember(signInViewModel) { signInActions(signInViewModel) }
    AccountScreen(
        body = account.toBody(signInHoldsAccountPage = signIn.holdsAccountPage),
        signInState = signIn.step,
        signInStatus = signIn.status,
        signInActions = actions,
        profileActions =
            AccountProfileActions(
                onSignOut = { accountViewModel.dispatch(AccountIntent.SignOut) },
                onToggleAnalytics = { enabled ->
                    accountViewModel.dispatch(AccountIntent.SetAnalyticsConsent(enabled))
                },
            ),
        onRetryProfile = { accountViewModel.dispatch(AccountIntent.RetryProfile) },
        onTabSelected = onTabSelected,
    )
}
