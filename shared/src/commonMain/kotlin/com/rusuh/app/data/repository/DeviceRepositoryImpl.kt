package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.DeviceCache
import com.rusuh.app.data.clock.parseStringDateMillis
import com.rusuh.app.data.network.newIdempotencyKey
import com.rusuh.app.data.platform.AttestationProvider
import com.rusuh.app.data.platform.DevAttestationProvider
import com.rusuh.app.data.platform.wallClockMillis
import com.rusuh.app.data.remote.DeviceApiService
import com.rusuh.app.data.remote.dto.RegisterDeviceRequestDto
import com.rusuh.app.data.storage.SecureStore
import com.rusuh.app.data.storage.TokenStore
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.repository.DeviceRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * [DeviceRepository] over `POST /queue/devices`. The install id is a random UUID made once and kept in the [SecureStore]
 * (Keystore / Keychain), so the same install always registers as the same device. Registration runs one at a time,
 * so concurrent callers share one call. Tokens and the install id are never logged.
 *
 * @param platform `android` or `ios`.
 * @param newInstallId source of a fresh install id; injectable for tests.
 * @param wallClock device wall clock, used ONLY to judge the cached token expiry while the [serverClock] is not synced
 * yet (the first call after launch); injectable for tests.
 */
@Suppress("LongParameterList") // the collaborators of one use case; each is a separate seam
class DeviceRepositoryImpl(
    private val api: DeviceApiService,
    private val tokens: TokenStore,
    private val secureStore: SecureStore,
    private val cache: DeviceCache,
    private val serverClock: ServerClock,
    private val platform: String,
    private val attestationProvider: AttestationProvider = DevAttestationProvider(),
    private val newInstallId: () -> String = ::newIdempotencyKey,
    private val wallClock: () -> Long = ::wallClockMillis,
) : DeviceRepository {
    private val lock = Mutex()

    /** Counts completed registrations, to tell a refresh somebody else just did while we waited for the lock. */
    @Volatile
    private var registrations = 0

    override suspend fun ensureRegistered(forceRefresh: Boolean): DomainResult<Unit> {
        val seen = registrations
        return lock.withLock {
            when {
                // Another caller registered while this one waited: that token is fresh, do not register again.
                forceRefresh && registrations != seen -> DomainResult.Success(Unit)
                !forceRefresh && hasUsableToken() -> DomainResult.Success(Unit)
                else -> register()
            }
        }
    }

    /**
     * A stored token that has not expired. With a synced server clock the expiry is compared on the server timeline;
     * before the first response the device wall clock stands in, with a one day margin (conservative: a token that
     * could be expired is replaced, one that is days from expiry is kept).
     */
    private suspend fun hasUsableToken(): Boolean {
        val expiresAt = cache.getTokenExpiresAt()
        val notExpired =
            when {
                expiresAt == null -> true
                serverClock.isSynced -> serverClock.nowMillis() + EXPIRY_SKEW_MILLIS < expiresAt
                else -> wallClock() + UNSYNCED_MARGIN_MILLIS < expiresAt
            }
        return notExpired && !tokens.getDeviceToken().isNullOrBlank()
    }

    private suspend fun register(): DomainResult<Unit> {
        val request =
            RegisterDeviceRequestDto(
                platform = platform,
                installId = installId(),
                attestation = attestationProvider.attestation(),
            )
        val result = api.register(request).toDomainResult { dto -> dto.takeIf { it.deviceToken.isNotBlank() } }
        return when (result) {
            is DomainResult.Success -> {
                registrations++
                tokens.setDeviceToken(result.data.deviceToken)
                val expiresAt = result.data.expiresAt?.let(::parseStringDateMillis)
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
        const val UNSYNCED_MARGIN_MILLIS = 24 * 60 * 60 * 1_000L
    }
}
