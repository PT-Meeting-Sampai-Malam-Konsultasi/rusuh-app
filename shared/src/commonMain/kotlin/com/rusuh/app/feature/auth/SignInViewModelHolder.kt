package com.rusuh.app.feature.auth

import androidx.lifecycle.ViewModel

/**
 * Keeps a [SignInViewModel] in a navigation back-stack entry's ViewModelStore, so the step (and the pending code)
 * survives rotation, tab switches and recomposition, and its work is cancelled when the entry is dropped.
 */
class SignInViewModelHolder(val viewModel: SignInViewModel) : ViewModel() {
    override fun onCleared() {
        viewModel.clear()
    }
}
