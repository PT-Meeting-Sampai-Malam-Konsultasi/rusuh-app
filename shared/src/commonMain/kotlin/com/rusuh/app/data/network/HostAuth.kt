package com.rusuh.app.data.network

import com.rusuh.app.data.network.session.SessionRefreshCoordinator
import com.rusuh.app.data.storage.TokenStore

/**
 * Per-host token policy for [ApiClient.forHost]. [refreshCoordinator] turns on the 401 -> refresh -> retry-once
 * handling; null leaves a 401 untouched. All three hosts use the default policy today (device token and Bearer on every
 * call, refresh on): the contract accepts `bearerAuth` on payment endpoints as the alternative to the web-only
 * `__Host-pay_session` cookie, and queue calls need the device token. Queue and admission tokens are never part of
 * the policy; ApiServices add them per call.
 */
class HostAuth(
    val tokenStore: TokenStore,
    val refreshCoordinator: SessionRefreshCoordinator? = null,
    val sendDeviceToken: Boolean = true,
    val sendBearer: Boolean = true,
)
