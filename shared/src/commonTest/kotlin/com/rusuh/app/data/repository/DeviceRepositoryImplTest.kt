package com.rusuh.app.data.repository

import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.domain.model.ErrorCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 24 * 60 * 60 * 1_000L

class DeviceRepositoryImplTest {
    private val devicesLine = "POST /queue/devices"

    @Test
    fun registersOnTheFirstCallWithPlatformAndInstallIdAndStoresTheToken() =
        runTest {
            val h = QueueHarness()
            h.backend.on(devicesLine, Reply.created(DEVICE_JSON))

            val result = h.devices.ensureRegistered()

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals("mock-device-token", h.tokens.getDeviceToken())
            val body = Json.parseToJsonElement(h.backend.seen.single().body).jsonObject
            assertEquals("android", body.getValue("platform").jsonPrimitive.content)
            assertEquals("install-1", body.getValue("installId").jsonPrimitive.content)
            assertEquals("dev", body.getValue("attestation").jsonPrimitive.content)
            assertEquals(3, body.size)
            assertNull(h.backend.seen.single().headers["X-Turnstile-Token"])
        }

    @Test
    fun doesNotRegisterAgainWhileTheTokenIsValid() =
        runTest {
            val h = QueueHarness()
            h.backend.on(devicesLine, Reply.created(DEVICE_JSON))

            h.devices.ensureRegistered()
            h.devices.ensureRegistered()
            h.devices.ensureRegistered()

            assertEquals(1, h.backend.callsTo(devicesLine))
        }

    @Test
    fun theInstallIdIsCreatedOnceAndSurvivesReRegistration() =
        runTest {
            var created = 0
            val h = QueueHarness()
            val devices =
                DeviceRepositoryImpl(
                    api = com.rusuh.app.data.remote.DeviceApiService(h.backend.api),
                    tokens = h.tokens,
                    secureStore = h.secure,
                    cache = h.deviceCache,
                    serverClock = h.clock,
                    platform = "ios",
                    newInstallId = { "install-${++created}" },
                )
            h.backend.on(devicesLine, Reply.created(DEVICE_JSON))

            devices.ensureRegistered()
            devices.ensureRegistered(forceRefresh = true)

            assertEquals(1, created)
            val ids =
                h.backend.seen.map {
                    Json.parseToJsonElement(
                        it.body,
                    ).jsonObject.getValue("installId").jsonPrimitive.content
                }
            assertEquals(listOf("install-1", "install-1"), ids)
            assertEquals("install-1", h.secure.entries["auth_install_id"])
            assertEquals(
                "ios",
                Json.parseToJsonElement(
                    h.backend.seen.first().body,
                ).jsonObject.getValue("platform").jsonPrimitive.content,
            )
        }

    @Test
    fun forceRefreshRegistersEvenWithAValidToken() =
        runTest {
            val h = QueueHarness()
            h.backend.on(
                devicesLine,
                Reply.created(DEVICE_JSON),
                Reply.created(DEVICE_JSON.replace("mock-device", "second")),
            )
            h.devices.ensureRegistered()

            val result = h.devices.ensureRegistered(forceRefresh = true)

            assertEquals(DomainResult.Success(Unit), result)
            assertEquals(2, h.backend.callsTo(devicesLine))
            assertEquals("second-token", h.tokens.getDeviceToken())
        }

    @Test
    fun anExpiredTokenIsReplacedWithoutBeingAsked() =
        runTest {
            val h = QueueHarness()
            // Expires 2026-11-11T10:37:59Z: 30 s after the fixture clock, inside the 60 s safety margin.
            h.backend.on(devicesLine, Reply.created("""{"deviceToken":"short","expiresAt":"2026-11-11T10:37:59Z"}"""))
            h.devices.ensureRegistered()
            assertEquals("short", h.tokens.getDeviceToken())

            h.backend.on(devicesLine, Reply.created(DEVICE_JSON))
            h.devices.ensureRegistered()

            assertEquals("mock-device-token", h.tokens.getDeviceToken())
            assertEquals(2, h.backend.callsTo(devicesLine))
        }

    @Test
    fun aTokenThatExpiresLaterIsKept() =
        runTest {
            val h = QueueHarness()
            h.backend.on(devicesLine, Reply.created("""{"deviceToken":"long","expiresAt":"2026-11-11T11:37:29Z"}"""))
            h.devices.ensureRegistered()

            h.devices.ensureRegistered()

            assertEquals(1, h.backend.callsTo(devicesLine))
        }

    private suspend fun unsyncedRepository(
        h: QueueHarness,
        expiresAt: Long,
    ): DeviceRepositoryImpl {
        h.tokens.setDeviceToken("old-token")
        h.deviceCache.setTokenExpiresAt(expiresAt)
        return DeviceRepositoryImpl(
            api = com.rusuh.app.data.remote.DeviceApiService(h.backend.api),
            tokens = h.tokens,
            secureStore = h.secure,
            cache = h.deviceCache,
            // Not synced: it counts from an arbitrary small start and must not be compared with the expiry.
            serverClock = TestServerClock(now = 5_000L, synced = false),
            platform = "android",
            newInstallId = { "install-1" },
            wallClock = { FIXTURE_NOW_MILLIS },
        )
    }

    @Test
    fun beforeTheClockSyncsAFarFromExpiredTokenIsKept() =
        runTest {
            val h = QueueHarness()
            h.backend.on(devicesLine, Reply.created(DEVICE_JSON))
            val devices = unsyncedRepository(h, expiresAt = FIXTURE_NOW_MILLIS + 3 * DAY)

            assertEquals(DomainResult.Success(Unit), devices.ensureRegistered())

            assertEquals(0, h.backend.callsTo(devicesLine))
            assertEquals("old-token", h.tokens.getDeviceToken())
        }

    @Test
    fun beforeTheClockSyncsAnExpiredOrNearlyExpiredTokenIsReplaced() =
        runTest {
            listOf(FIXTURE_NOW_MILLIS - DAY, FIXTURE_NOW_MILLIS + 3_600_000L).forEach { expiresAt ->
                val h = QueueHarness()
                h.backend.on(devicesLine, Reply.created(DEVICE_JSON))
                val devices = unsyncedRepository(h, expiresAt)

                devices.ensureRegistered()

                assertEquals(1, h.backend.callsTo(devicesLine), "expires at $expiresAt")
                assertEquals("mock-device-token", h.tokens.getDeviceToken())
            }
        }

    @Test
    fun errorsComeBackAsDomainErrorsAndStoreNothing() =
        runTest {
            val cases =
                listOf(
                    Triple(400, "VALIDATION_FAILED", ErrorCode.VALIDATION_FAILED),
                    Triple(429, "RATE_LIMITED", ErrorCode.RATE_LIMITED),
                    Triple(503, "VERIFICATION_UNAVAILABLE", ErrorCode.VERIFICATION_UNAVAILABLE),
                )
            cases.forEach { (status, code, expected) ->
                val h = QueueHarness()
                h.backend.on(devicesLine, Reply.problem(status, code, retryAfter = 12))

                val result = assertIs<DomainResult.Error>(h.devices.ensureRegistered())

                assertEquals(status, result.code)
                assertEquals(expected, result.errorCode)
                assertEquals(12, result.retryAfterSeconds)
                assertNull(h.tokens.getDeviceToken(), "$status must not store a token")
            }
        }

    @Test
    fun aFailedRegistrationIsRetriedOnTheNextCall() =
        runTest {
            val h = QueueHarness()
            h.backend.on(devicesLine, Reply.problem(503, "VERIFICATION_UNAVAILABLE"), Reply.created(DEVICE_JSON))

            assertIs<DomainResult.Error>(h.devices.ensureRegistered())
            assertEquals(DomainResult.Success(Unit), h.devices.ensureRegistered())

            assertEquals("mock-device-token", h.tokens.getDeviceToken())
        }

    @Test
    fun offlineAndTheRealHostsNotFoundAreReported() =
        runTest {
            val offline = QueueHarness()
            offline.backend.on(devicesLine, Reply.offline())
            assertEquals(DomainResult.NetworkError, offline.devices.ensureRegistered())

            val skeleton = QueueHarness() // unscripted: the real host answers 404 to every route today
            assertEquals(404, assertIs<DomainResult.Error>(skeleton.devices.ensureRegistered()).code)
            assertNull(skeleton.tokens.getDeviceToken())
        }

    @Test
    fun aSuccessWithoutATokenIsATechnicalError() =
        runTest {
            val h = QueueHarness()
            h.backend.on(devicesLine, Reply.created("""{"expiresAt":"2030-01-01T00:00:00Z"}"""))

            assertEquals(DomainResult.TechnicalError, h.devices.ensureRegistered())
            assertTrue(h.tokens.getDeviceToken().isNullOrBlank())
        }
}
