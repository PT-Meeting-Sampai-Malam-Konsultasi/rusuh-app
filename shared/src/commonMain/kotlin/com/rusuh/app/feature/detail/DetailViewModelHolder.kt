package com.rusuh.app.feature.detail

import androidx.lifecycle.ViewModel

/**
 * Keeps a [DetailViewModel] alive in a navigation back-stack entry's ViewModelStore, so the form
 * and the order window survive recomposition, rotation and re-entry of the route.
 *
 * Until FR-17-M1 #82 the order window lives in the stub repository only (process-local): after
 * process death the hold restarts.
 *
 * TODO(FR-16-M1 #81): cancel the ViewModel's scope (and any in-flight booking) in onCleared.
 */
class DetailViewModelHolder(val viewModel: DetailViewModel) : ViewModel() {
    override fun onCleared() {
        viewModel.clear()
    }
}
