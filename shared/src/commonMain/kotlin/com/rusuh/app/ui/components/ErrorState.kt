package com.rusuh.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.rusuh.app.ui.theme.RusuhSpacing

/**
 * Full-screen error state with a retry affordance.
 *
 * Centres a message and a retry button. [onRetry] is wired by the caller to the
 * retry/refresh intent so this component stays free of any MVI dependency and can
 * be reused by any screen.
 *
 * @param message user-facing error text.
 * @param onRetry invoked when the user taps retry.
 */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(RusuhSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(RusuhSpacing.xl))
        Button(onClick = onRetry) {
            Text(text = "Coba lagi")
        }
    }
}
