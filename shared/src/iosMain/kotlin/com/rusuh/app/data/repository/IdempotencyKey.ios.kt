package com.rusuh.app.data.repository

import platform.Foundation.NSUUID

internal actual fun newIdempotencyKey(): String = NSUUID().UUIDString()
