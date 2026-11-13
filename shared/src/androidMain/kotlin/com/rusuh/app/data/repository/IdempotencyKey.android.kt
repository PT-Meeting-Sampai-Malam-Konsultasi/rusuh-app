package com.rusuh.app.data.repository

import java.util.UUID

internal actual fun newIdempotencyKey(): String = UUID.randomUUID().toString()
