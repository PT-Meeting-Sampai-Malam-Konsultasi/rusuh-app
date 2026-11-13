package com.rusuh.app.feature.consent

import androidx.compose.runtime.Immutable
import com.rusuh.app.mvi.MviEffect
import com.rusuh.app.mvi.MviIntent
import com.rusuh.app.mvi.MviState

@Immutable
data class ConsentState(
    val isLoading: Boolean = false,
) : MviState

sealed interface ConsentIntent : MviIntent {
    data object Accept : ConsentIntent

    data object Decline : ConsentIntent
}

sealed interface ConsentEffect : MviEffect {
    data object Done : ConsentEffect
}
