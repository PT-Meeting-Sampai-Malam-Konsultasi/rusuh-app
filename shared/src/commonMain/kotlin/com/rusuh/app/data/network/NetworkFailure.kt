package com.rusuh.app.data.network

/**
 * True when [t] means the server could not be reached (offline, DNS, connection or request timeout),
 * as opposed to a client-side bug such as an unreadable body. Each platform engine throws its own types.
 */
expect fun isNetworkFailure(t: Throwable): Boolean
