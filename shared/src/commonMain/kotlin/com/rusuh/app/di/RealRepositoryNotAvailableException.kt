package com.rusuh.app.di

/**
 * Thrown in strict mode (no stub fallback) when [BackendMode.Real] resolves a repository that has no network
 * implementation yet.
 */
class RealRepositoryNotAvailableException(
    repository: String,
) : IllegalStateException(
        "No real implementation of $repository yet: run with BackendMode.Stub, or build the network repository " +
            "and bind it in liveDataModule().",
    )
