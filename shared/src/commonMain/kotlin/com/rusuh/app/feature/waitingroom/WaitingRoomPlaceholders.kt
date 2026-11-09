@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.feature.waitingroom

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rusuh.app.ui.theme.RusuhColors
import com.rusuh.app.ui.theme.RusuhSpacing
import org.jetbrains.compose.resources.stringResource
import rusuh_app.shared.generated.resources.Res
import rusuh_app.shared.generated.resources.waiting_loading
import rusuh_app.shared.generated.resources.waiting_loading_description

@Composable
internal fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = PLACEHOLDER_MIN_HEIGHT).padding(RusuhSpacing.xl),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
internal fun LoadingContent() {
    val loadingDescription = stringResource(Res.string.waiting_loading_description)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = loadingDescription },
    ) {
        CircularProgressIndicator(color = RusuhColors.Pink)
        Spacer(Modifier.height(RusuhSpacing.xl))
        Text(
            text = stringResource(Res.string.waiting_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = RusuhColors.InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun CenteredMessage(
    title: String,
    body: String,
    titleColor: Color = RusuhColors.Ink,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$title. $body" },
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
            color = RusuhColors.InkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

private val PLACEHOLDER_MIN_HEIGHT = 400.dp
