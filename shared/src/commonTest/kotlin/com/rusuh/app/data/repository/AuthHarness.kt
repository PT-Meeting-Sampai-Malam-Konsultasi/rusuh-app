package com.rusuh.app.data.repository

import com.rusuh.app.data.network.HostAuth
import com.rusuh.app.data.network.session.ApiTokenRefresher
import com.rusuh.app.data.network.session.SessionEvents
import com.rusuh.app.data.network.session.SessionManager
import com.rusuh.app.data.network.session.SessionRefreshCoordinator
import com.rusuh.app.data.remote.AuthApiService
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

/** Synthetic fixtures only: no real emails, names or tokens. */
const val CHALLENGE_JSON = """{"challengeId":"ch_test_1","expiresAt":"2026-11-14T10:50:00Z","resendAfterSeconds":60}"""

const val USER_JSON =
    """{"userId":"usr_test_1","email":"test.user@example.test","fullName":"Test User","locale":"id"}"""

const val SESSION_JSON =
    """{"accessToken":"access-1","expiresIn":900,"refreshToken":"refresh-1","isNewAccount":false,"user":$USER_JSON}"""

const val REFRESHED_JSON =
    """{"accessToken":"access-2","expiresIn":900,"refreshToken":"refresh-2","isNewAccount":false,"user":$USER_JSON}"""

/** What the live backend answers on 2026-11-12 (type "about:blank", Indonesian detail, code outside the contract). */
const val LIVE_BAD_REQUEST =
    """{"type":"about:blank","title":"Bad Request","status":400,""" +
        """"detail":"Validasi gagal: \"email\" is required","code":"BAD_REQUEST"}"""

const val LIVE_UNAUTHORIZED =
    """{"type":"about:blank","title":"Unauthorized","status":401,""" +
        """"detail":"Token akun tidak ditemukan","code":"UNAUTHORIZED"}"""

const val LIVE_EXPIRED =
    """{"type":"about:blank","title":"OTP challenge expired","status":410,"code":"CHALLENGE_EXPIRED",""" +
        """"detail":"Kode sudah kedaluwarsa, minta kode baru"}"""

fun problemReply(
    status: Int,
    body: String,
    retryAfter: Int? = null,
) = Reply(status, body, problem = true, retryAfter = retryAfter)

fun wrongCodeReply(attemptsLeft: Int) =
    problemReply(
        400,
        """{"type":"https://api.rusuh.id/problems/wrong-code","title":"Kode salah","status":400,""" +
            """"code":"WRONG_CODE","detail":"The code does not match.","attemptsLeft":$attemptsLeft}""",
    )

fun codeReply(
    status: Int,
    code: String,
) = problemReply(
    status,
    """{"type":"https://api.rusuh.id/problems/x","title":"t","status":$status,"code":"$code"}""",
)

/** Device registration stand-in that records calls; [result] can be made to fail. */
class FakeDevices(
    var result: DomainResult<Unit> = DomainResult.Success(Unit),
    private val throwing: Boolean = false,
) : DeviceRepository {
    var calls = 0
    val forced = mutableListOf<Boolean>()
    var resultFor: ((Boolean) -> DomainResult<Unit>)? = null

    override suspend fun ensureRegistered(forceRefresh: Boolean): DomainResult<Unit> {
        calls++
        forced += forceRefresh
        check(!throwing) { "keystore unavailable" }
        return resultFor?.invoke(forceRefresh) ?: result
    }
}

/** The whole session stack on one [FakeBackend]: tokens, 401 refresh, [SessionManager] and [RealAuthRepository]. */
class AuthHarness(
    managerScope: CoroutineScope,
    val devices: FakeDevices = FakeDevices(),
    // Infinite by default: runTest skips virtual time while the mock engine works on a real thread.
    logoutTimeout: Duration? = Duration.INFINITE,
) {
    val secure = FakeSecureStore()
    val tokens = SecureTokenStore(secure)
    val events = SessionEvents()
    val backend: FakeBackend
    val session: SessionManager
    val repository: RealAuthRepository

    init {
        lateinit var fake: FakeBackend
        val coordinator =
            SessionRefreshCoordinator(tokens, lazy { ApiTokenRefresher(fake.api) }, events)
        fake = FakeBackend(HostAuth(tokens, coordinator))
        backend = fake
        session = SessionManager(tokens, events, managerScope)
        val refresher = ApiTokenRefresher(fake.api)
        val api = AuthApiService(backend.api)
        repository =
            if (logoutTimeout == null) {
                RealAuthRepository(api, devices, session, refresher)
            } else {
                RealAuthRepository(api, devices, session, refresher, logoutTimeout)
            }
    }

    /** A signed-in state as left by a previous login: access token in memory, refresh token in storage. */
    suspend fun seedSession(
        access: String? = "access-1",
        refresh: String? = "refresh-1",
    ) {
        tokens.setDeviceToken("device-1")
        tokens.accessToken = access
        refresh?.let { tokens.setRefreshToken(it) }
    }
}
