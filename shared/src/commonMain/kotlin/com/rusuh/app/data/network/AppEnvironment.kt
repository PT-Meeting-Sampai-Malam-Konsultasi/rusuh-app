package com.rusuh.app.data.network

/** Backend the app talks to. Chosen by the platform entry point (debug = [Mock], release = [Production]). */
enum class AppEnvironment {
    /** Local Mockoon mock of the API contract. Debug builds only. */
    Mock,
    Staging,
    Production,
    ;

    companion object {
        /** Parses an entry-point value (case-insensitive); fails loudly on anything unknown. */
        fun fromName(name: String): AppEnvironment =
            entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Unknown app environment '$name'; expected one of ${entries.joinToString { it.name }}",
                )
    }
}
