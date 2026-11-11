package com.rusuh.app.data.platform

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun wallClockMillis(): Long = (NSDate().timeIntervalSince1970 * MILLIS_PER_SECOND).toLong()

private const val MILLIS_PER_SECOND = 1_000.0
