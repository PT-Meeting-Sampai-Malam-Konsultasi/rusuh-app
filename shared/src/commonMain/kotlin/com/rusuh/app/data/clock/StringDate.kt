package com.rusuh.app.data.clock

import kotlinx.datetime.Instant

/** Epoch millis of an ISO-8601 timestamp such as `2026-11-14T10:40:00Z` or `...T17:40:00+07:00`; null if malformed. */
internal fun parseStringDateMillis(text: String): Long? =
    runCatching { Instant.parse(text.trim()).toEpochMilliseconds() }.getOrNull()
