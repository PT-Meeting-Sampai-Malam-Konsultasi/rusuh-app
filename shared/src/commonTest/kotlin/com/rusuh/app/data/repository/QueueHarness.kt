package com.rusuh.app.data.repository

import com.rusuh.app.data.cache.DeviceCache
import com.rusuh.app.data.cache.QueueCache
import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.remote.DeviceApiService
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.QueueApiService
import com.rusuh.app.data.storage.FakeAppPreferences
import com.rusuh.app.data.storage.FakeSecureStore
import com.rusuh.app.data.storage.SecureTokenStore
import com.rusuh.app.domain.clock.ServerClock
import kotlin.random.Random

/** A settable server clock; the harness advances it by every pacing delay, like real time would. */
class TestServerClock(
    var now: Long = FIXTURE_NOW_MILLIS,
) : ServerClock {
    override fun nowMillis(): Long = now
}

/** Everything the real repositories need, on one [FakeBackend], with recorded pacing delays and no real waiting. */
class QueueHarness(
    language: AppLanguage = AppLanguage.Indonesian,
    seed: Int = 7,
) {
    val backend = FakeBackend()
    val secure = FakeSecureStore()
    val prefs = FakeAppPreferences()
    val tokens = SecureTokenStore(secure)
    val queueCache = QueueCache(prefs)
    val deviceCache = DeviceCache(prefs)
    val clock = TestServerClock()
    val locale = LocaleProvider { language }
    val delays = mutableListOf<Long>()

    val pacing =
        QueuePacing(
            delay = { millis ->
                delays += millis
                clock.now += millis
            },
            random = Random(seed),
        )

    val devices =
        RealDeviceRepository(
            api = DeviceApiService(backend.api),
            tokens = tokens,
            secureStore = secure,
            cache = deviceCache,
            serverClock = clock,
            platform = "android",
            newInstallId = { "install-1" },
        )

    val queue =
        RealQueueRepository(
            queueApi = QueueApiService(backend.api),
            eventApi = EventApiService(backend.api),
            devices = devices,
            tokens = tokens,
            cache = queueCache,
            serverClock = clock,
            localeProvider = locale,
            pacing = pacing,
        )

    val myQueues =
        RealMyQueuesRepository(
            queueApi = QueueApiService(backend.api),
            eventApi = EventApiService(backend.api),
            tokens = tokens,
            cache = queueCache,
            localeProvider = locale,
        )

    /** The happy-path device and event routes every queue test needs. */
    fun scriptBasics() {
        backend.on("POST /devices", Reply.created(DEVICE_JSON))
        backend.on("GET /events", Reply.ok(EVENTS_JSON))
    }
}
