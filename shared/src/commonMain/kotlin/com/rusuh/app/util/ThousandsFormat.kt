package com.rusuh.app.util

fun Long.groupThousands(): String {
    val digits = toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return if (this < 0) "-$grouped" else grouped
}
