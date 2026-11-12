package com.rusuh.app.feature.account

import androidx.lifecycle.ViewModel

/** Keeps an [AccountViewModel] in a back-stack entry, see `SignInViewModelHolder`. */
class AccountViewModelHolder(val viewModel: AccountViewModel) : ViewModel() {
    override fun onCleared() {
        viewModel.clear()
    }
}
