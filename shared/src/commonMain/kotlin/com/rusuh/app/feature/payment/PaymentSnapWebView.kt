package com.rusuh.app.feature.payment

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
@Suppress("LongParameterList")
expect fun PaymentSnapWebView(
    url: String,
    bookingId: String,
    modifier: Modifier,
    onClose: () -> Unit,
    onLoading: (Boolean) -> Unit,
    onLoadError: () -> Unit,
    onNotice: () -> Unit,
)
