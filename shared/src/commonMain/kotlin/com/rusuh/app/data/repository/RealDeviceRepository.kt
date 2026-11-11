package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.DeviceCache
import com.rusuh.app.data.clock.parseRfc3339Millis
import com.rusuh.app.data.network.newIdempotencyKey
import com.rusuh.app.data.remote.DeviceApiService
import com.rusuh.app.data.remote.dto.RegisterDeviceRequestDto
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.repository.DeviceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [DeviceRepository] over `POST /devices`. The install id is a random UUID made once and kept in the [SecureStore]
 * (Keystore / Keychain), so the same install always registers as the same device. Registration runs one at a time,
 * so concurrent callers share one call. Tokens and the install id are never logged.
 *
 * @param platform `android` or `ios`.
 * @param newInstallId source of a fresh install id; injectable for tests.
 */
@Suppress("LongParameterList") // the collaborators of one use case; each is a separate seam
class RealDeviceRepository(
    private val api: DeviceApiService,
    private val tokens: TokenStore,
    private val secureStore: SecureStore,
    private val cache: DeviceCache,
    private val serverClock: ServerClock,
    private val platform: String,
    private val newInstallId: () -> String = ::newIdempotencyKey,
) : DeviceRepository {
    private val lock = Mutex()

    override suspend fun ensureRegistered(forceRefresh: Boolean): DomainResult<Unit> =
        lock.withLock {
            if (!forceRefresh && hasUsableToken()) DomainResult.Success(Unit) else register()
        }

    private suspend fun hasUsableToken(): Boolean {
        val expiresAt = cache.getTokenExpiresAt()
        val now = serverClock.nowMillis()
        // An unsynced clock reads 0: then only a 401 can tell us the token is bad.
        val notExpired = expiresAt == null || now <= 0L || now + EXPIRY_SKEW_MILLIS < expiresAt
        return !tokens.getDeviceToken().isNullOrBlank() && notExpired
    }

    private suspend fun register(): DomainResult<Unit> {
        val request = RegisterDeviceRequestDto(platform = platform, installId = installId())
        val result = api.register(request).toDomainResult { dto -> dto.takeIf { it.deviceToken.isNotBlank() } }
        return when (result) {
            is DomainResult.Success -> {
                tokens.setDeviceToken(result.data.deviceToken)
                val expiresAt = result.data.expiresAt?.let(::parseRfc3339Millis)
                if (expiresAt != null) cache.setTokenExpiresAt(expiresAt) else cache.clearTokenExpiresAt()
                DomainResult.Success(Unit)
            }
            // A 201 without a token cannot be used: a client-side problem, not an empty result.
            is DomainResult.Empty -> DomainResult.TechnicalError
            is DomainResult.Error -> result
            DomainResult.NetworkError -> DomainResult.NetworkError
            DomainResult.TechnicalError -> DomainResult.TechnicalError
        }
    }

    private suspend fun installId(): String =
        secureStore.getString(KEY_INSTALL_ID)?.takeIf { it.isNotBlank() }
            ?: newInstallId().also { secureStore.putString(KEY_INSTALL_ID, it) }

    private companion object {
        const val KEY_INSTALL_ID = "auth_install_id"
        const val EXPIRY_SKEW_MILLIS = 60_000L
    }
}
