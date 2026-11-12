@file:Suppress("LongParameterList")

package com.rusuh.app.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rusuh.app.feature.auth.SignInActions
import com.rusuh.app.feature.auth.SignInContent
import com.rusuh.app.feature.auth.SignInUiState
import com.rusuh.app.feature.auth.SignInVariant
import com.rusuh.app.feature.queues.AppLanguageOption
import com.rusuh.app.feature.queues.MyQueuesHeader
import com.rusuh.app.ui.components.RusuhBottomBar
import com.rusuh.app.ui.components.RusuhTab
import com.rusuh.app.ui.theme.RusuhBrandColors
import com.rusuh.app.ui.theme.RusuhSpacing

/**
 * Akun tab (AUTH-00-M5 #102): the normal tab chrome (RUSUH! header with ID|EN, bottom bar with
 * Akun selected) around [AccountContent].
 *
 * @param signInState the login step shown while signed out (placeholder flow until #77).
 * @param signInActions callbacks of the login steps; finishing the flow is what makes the caller
 *   pass `isSignedIn = true`.
 */
@Composable
fun AccountScreen(
    isSignedIn: Boolean,
    signInState: SignInUiState,
    modifier: Modifier = Modifier,
    signInActions: SignInActions = SignInActions(),
    profile: AccountProfileUi = AccountProfileUi(name = "", email = "", emailVerified = true),
    profileActions: AccountProfileActions = AccountProfileActions(onSignOut = {}),
    onTabSelected: (RusuhTab) -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = RusuhBrandColors.Paper,
        topBar = { MyQueuesHeader(language = AppLanguageOption.Indonesian) },
        bottomBar = { RusuhBottomBar(selectedTab = RusuhTab.Akun, onTabSelected = onTabSelected) },
    ) { innerPadding ->
        AccountContent(
            isSignedIn = isSignedIn,
            signInState = signInState,
            signInActions = signInActions,
            profile = profile,
            profileActions = profileActions,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}

/**
 * The single seam of the Account tab body: the compact login while signed out, the account page
 * once signed in (`AccountProfileContent`, ACCOUNT-00 #104).
 */
@Composable
fun AccountContent(
    isSignedIn: Boolean,
    signInState: SignInUiState,
    modifier: Modifier = Modifier,
    signInActions: SignInActions = SignInActions(),
    profile: AccountProfileUi = AccountProfileUi(name = "", email = "", emailVerified = true),
    profileActions: AccountProfileActions = AccountProfileActions(onSignOut = {}),
) {
    if (isSignedIn) {
        // TODO(AUTH-00 #77): feed [profile] from the real session (GET /me) instead of the placeholder flow.
        AccountProfileContent(profile = profile, actions = profileActions, modifier = modifier)
    } else {
        CompactSignIn(signInState, signInActions, modifier)
    }
}

/** Compact login (#102): only what the full screen shows above its first thick rule. */
@Composable
private fun CompactSignIn(
    state: SignInUiState,
    actions: SignInActions,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState()),
    ) {
        SignInContent(
            state = state,
            actions = actions,
            variant = SignInVariant.Compact,
            modifier = Modifier.padding(PaddingValues(horizontal = RusuhSpacing.xl, vertical = RusuhSpacing.xl)),
        )
    }
}
