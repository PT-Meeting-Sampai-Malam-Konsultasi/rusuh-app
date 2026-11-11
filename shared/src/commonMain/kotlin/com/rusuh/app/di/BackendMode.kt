package com.rusuh.app.di

import com.rusuh.app.data.network.AppEnvironment

/**
 * Where repositories get their data (the `useStubs` switch), independent of which host [AppEnvironment] names.
 *
 * - [Stub]: repositories and the HTTP engine run in process on spec-based stub payloads. No backend, no network.
 * - [Real]: repositories use the network implementations over HTTP to the environment's hosts. With
 *   [AppEnvironment.Mock] that is the Mockoon server (see `NetworkConfig.MOCK_API`).
 */
enum class BackendMode {
    Stub,
    Real,
    ;

    companion object {
        /**
         * Default for every environment: [Real] (HTTP; to Mockoon for [AppEnvironment.Mock], to the real host
         * otherwise). [Stub] is an explicit debug opt-in only. [environment] is kept so a future environment can
         * differ.
         */
        @Suppress("UnusedParameter")
        fun defaultFor(environment: AppEnvironment): BackendMode = Real

        /**
         * The mode for [environment] given an optional entry-point override ([overrideName]: null or blank keeps
         * the default). Fails loudly on an unknown name, and on [Stub] for Production.
         */
        fun resolve(
            environment: AppEnvironment,
            overrideName: String? = null,
        ): BackendMode =
            (overrideName?.takeIf { it.isNotBlank() }?.let(::fromName) ?: defaultFor(environment))
                .also { verify(environment, it) }

        /** A release build must never ship fake data: [Stub] is rejected for [AppEnvironment.Production]. */
        fun verify(
            environment: AppEnvironment,
            mode: BackendMode,
        ) {
            require(!(mode == Stub && environment == AppEnvironment.Production)) {
                "BackendMode.Stub is not allowed for the Production environment"
            }
        }

        fun fromName(name: String): BackendMode =
            entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown backend mode '$name'; expected one of ${entries.joinToString { it.name }}",
                )
    }
}
