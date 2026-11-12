package com.rusuh.app.feature.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.tab_coming_soon

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
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }
}

/**
 * The single seam of the Account tab body: the compact login while signed out, the account page
 * once signed in. ACCOUNT-00 (#104, PR #106) swaps [SignedInAccountSlot] for `AccountProfileContent`
 * without touching the signed-out branch.
 */
@Composable
fun AccountContent(
    isSignedIn: Boolean,
    signInState: SignInUiState,
    modifier: Modifier = Modifier,
    signInActions: SignInActions = SignInActions(),
) {
    if (isSignedIn) {
        SignedInAccountSlot(modifier)
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

/**
 * Slot for the signed-in account page.
 * TODO(ACCOUNT-00 #104): replace with `AccountProfileContent` from feature/account (PR #106);
 *  until it merges this keeps the "coming soon" stub the tab always had.
 */
@Composable
private fun SignedInAccountSlot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(Res.string.tab_coming_soon, stringResource(RusuhTab.Akun.label)),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
