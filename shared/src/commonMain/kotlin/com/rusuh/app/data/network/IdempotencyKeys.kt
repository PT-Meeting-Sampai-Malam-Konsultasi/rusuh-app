package com.rusuh.app.data.network

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A fresh `Idempotency-Key` (random UUID, the format the contract requires).
 *
 * Rule: create ONE key per user attempt (one tap on "Pay") and send the SAME key on every retry of that attempt.
 * The server answers a repeat of the same key with the original result for 24 hours. Never auto-retry a
 * charging or creating POST with a different key: that is a second charge. A new user attempt gets a new key.
 * Send it with `idempotencyKey(key)`.
 */
@OptIn(ExperimentalUuidApi::class)
fun newIdempotencyKey(): String = Uuid.random().toString()
