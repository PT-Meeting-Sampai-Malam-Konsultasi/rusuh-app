package com.rusuh.app.data.repository

import com.rusuh.app.domain.model.MyQueue
import com.rusuh.app.domain.model.MyQueuePhase
import com.rusuh.app.domain.repository.MyQueuesRepository

/**
 * Stub repository returning sample queues so the use case, ViewModel and UI run
 * before the backend exists.
 *
 * shortcut: hard-coded sample. TODO(FR-APP-01-M4 #50): replace with the
 * network-backed repository.
 */
class StubMyQueuesRepository(
    private val queues: List<MyQueue> = SAMPLE_QUEUES,
) : MyQueuesRepository {
    override suspend fun getMyQueues(): List<MyQueue> = queues

    companion object {
        /** Covers every [MyQueuePhase] so previews exercise the whole row surface. */
        val SAMPLE_QUEUES: List<MyQueue> =
            listOf(
                MyQueue(
                    eventId = "evt-samudra",
                    title = "Samudra — Tur Gelombang 2026",
                    dateVenue = "12 Des 2026 · Stadion Madya, Jakarta",
                    position = 128,
                    nowServing = 102,
                    phase = MyQueuePhase.Live,
                    updatedAgoSeconds = 3,
                    etaMinutes = 12,
                ),
                MyQueue(
                    eventId = "evt-nyala",
                    title = "Nyala Fest",
                    dateVenue = "20 Des 2026 · GOR Saparua, Bandung",
                    position = 0,
                    nowServing = 4,
                    phase = MyQueuePhase.YourTurn,
                    updatedAgoSeconds = 1,
                ),
                MyQueue(
                    eventId = "evt-rimba",
                    title = "Rimba Raya Showcase",
                    dateVenue = "4 Jan 2027 · Lapangan Banteng, Jakarta",
                    position = 532,
                    nowServing = null,
                    phase = MyQueuePhase.QueueOpen,
                    updatedAgoSeconds = 8,
                ),
                MyQueue(
                    eventId = "evt-gema",
                    title = "Gema Akustik",
                    dateVenue = "18 Jan 2027 · Taman Budaya, Yogyakarta",
                    position = 76,
                    nowServing = 90,
                    phase = MyQueuePhase.Paused,
                    updatedAgoSeconds = 25,
                ),
                MyQueue(
                    eventId = "evt-pasar",
                    title = "Pasar Malam Senja",
                    dateVenue = "2 Feb 2027 · Pantai Losari, Makassar",
                    position = 2041,
                    nowServing = null,
                    phase = MyQueuePhase.Closed,
                    updatedAgoSeconds = 120,
                ),
            )
    }
}
