package com.rusuh.app.data.remote

import io.ktor.http.encodeURLPathPart

internal fun pathSegment(value: String): String = value.encodeURLPathPart()
