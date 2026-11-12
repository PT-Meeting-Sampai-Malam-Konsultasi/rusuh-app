package com.rusuh.app.data.remote.mapper

import com.rusuh.app.data.network.AppLanguage
import com.rusuh.app.data.remote.dto.EventsResponseDto
import com.rusuh.app.ui.components.EventStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LiveEventFixtureTest {
    @Test
    fun mapsTheLiveEventListShape() {
        val fixture =
            """
            {"items":[{"eventId":"evt_2026_wbh","name":"Way Back Home",
            "subtitle":"Konser tunggal satu malam di Jakarta","format":"Konser",
            "venue":"Beach City International Stadium","venueShort":"Beach City","city":"Jakarta",
            "timezone":"Asia/Jakarta","startsAt":"2026-11-23T12:00:00.000Z",
            "doorsOpenAt":"2026-11-23T10:00:00.000Z","saleOpensAt":"2026-11-01T03:00:00.000Z",
            "saleEndsAt":"2026-11-23T05:00:00.000Z","waitingRoomOpensAt":"2026-11-01T02:30:00.000Z",
            "state":"on_sale","capacity":8000,"priceFrom":{"amount":350000,"currency":"IDR"},
            "soldOutAt":null,"imageUrl":"https://example.test/placeholder.avif"}],
            "serverNow":"2026-11-12T11:08:37.280Z"}
            """.trimIndent()
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<EventsResponseDto>(fixture)

        assertEquals("2026-11-12T11:08:37.280Z", response.serverNow)
        val item = assertNotNull(response.items.single().toHomeEventItem(AppLanguage.Indonesian, null))
        assertEquals("evt_2026_wbh", item.id)
        assertEquals("Way Back Home", item.title)
        assertEquals("Jakarta", item.city)
        assertEquals(EventStatus.OnSale, item.status)
        assertEquals("Rp 350.000", item.priceFrom)
    }
}
