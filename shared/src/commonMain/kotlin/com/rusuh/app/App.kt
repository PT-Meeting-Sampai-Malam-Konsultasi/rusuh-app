package com.rusuh.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rusuh.app.ui.theme.RusuhTheme

@Composable
fun App() {
    RusuhTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Rusuh Booking Tiket",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}
