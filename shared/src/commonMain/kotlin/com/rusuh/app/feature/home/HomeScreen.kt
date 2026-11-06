package com.rusuh.app.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rusuh.app.ui.theme.RusuhTheme

/**
 * Minimal placeholder Home screen.
 *
 * This renders only the MVI [HomeState] (loading / error / event count). It is a
 * deliberate scaffold so M2 can wire M3's state together with M1's `EventCard`
 * without reworking the state layer.
 *
 * @param viewModel the [HomeViewModel] providing state and consuming intents.
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.dispatch(HomeIntent.LoadEvents)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        when {
            state.isLoading -> CircularProgressIndicator()
            state.error != null ->
                Text(
                    text = state.error ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                )
            else ->
                // TODO(M2): render event list with EventCard
                Text(
                    text = "Events: ${state.events.size}",
                    style = MaterialTheme.typography.bodyLarge,
                )
        }
    }
}

/**
 * Preview helper wrapping [HomeScreen] in [RusuhTheme].
 *
 * The `@Preview` annotation is intentionally omitted: it lives in
 * `compose.components.uiToolingPreview`, which this module does not depend on, and
 * M3 should not add a dependency just for a preview. M2 can annotate when it adds
 * the real UI and the tooling dependency.
 */
@Suppress("UnusedPrivateMember") // Preview seam for M2; becomes @Preview-annotated there.
@Composable
private fun HomeScreenPreview() {
    RusuhTheme {
        HomeScreen(viewModel = HomeViewModel(StubHomeRepository()))
    }
}
