package com.rusuh.app.ui

import androidx.compose.runtime.Composable

/**
 * Intercepts the system back gesture / button while [enabled] (Android). iOS has no system
 * back button, so the actual there is a no-op. commonMain has no BackHandler in this
 * Compose Multiplatform version, hence the expect/actual.
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
)
