package com.rusuh.app.data.network.session

import kotlinx.coroutines.flow.StateFlow

/**
 * Read-only view of the signed-in state. Extracted from [SessionManager] to enable testing
 * with a minimal fake (the full SessionManager has final methods that cannot be overridden).
 */
interface SessionStateProvider {
    /** True when the user is signed in. */
    val isSignedIn: StateFlow<Boolean>
}
