package com.rusuh.app.di

/** Thrown when [BackendMode.Real] resolves a repository whose network implementation does not exist yet. */
class RealRepositoryNotAvailableException(
    repository: String,
) : IllegalStateException(
        "No real implementation of $repository yet: run with BackendMode.Stub, or build the network repository " +
            "and bind it in dataModule(BackendMode.Real).",
    )
