@file:Suppress("MatchingDeclarationName")

package com.rusuh.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rusuh.app.ui.theme.RusuhSpacing
import com.rusuh.app.ui.theme.RusuhTheme

// Preview-style sample composables (no @Preview annotation, see QueuePositionCardPreviews).

@Composable
internal fun OtpCodeInputStatesPreview() {
    RusuhTheme {
        Column(
            Modifier.padding(RusuhSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(RusuhSpacing.xl),
        ) {
            OtpCodeInput(value = "", onValueChange = {})
            OtpCodeInput(value = "123", onValueChange = {})
            OtpCodeInput(value = "123456", onValueChange = {})
            OtpCodeInput(value = "654321", onValueChange = {}, state = OtpCodeState.Error)
            OtpCodeInput(value = "", onValueChange = {}, state = OtpCodeState.Disabled)
        }
    }
}
