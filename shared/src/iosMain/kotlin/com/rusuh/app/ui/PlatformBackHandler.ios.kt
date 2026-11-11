package com.rusuh.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS has no system back button; the sheet closes through its own "Nanti saja" action.
}
