package com.rusuh.app.feature.home

/**
 * Data seam for the Home screen.
 *
 * This interface is the contract M4 (API integration) will implement against the
 * real backend. Until then [StubHomeRepository] returns an empty list so the MVI
 * layer and the placeholder UI can be built and tested independently.
 */
interface HomeRepository {
    /** Fetch the list of events whose tickets are sold online. */
    suspend fun getEvents(): List<HomeEventItem>
}

/**
 * No-op implementation used until M4 wires the real API.
 *
 * shortcut: returns an empty list; swap for the real network-backed implementation
 * once M4 lands.
 */
class StubHomeRepository : HomeRepository {
    override suspend fun getEvents(): List<HomeEventItem> = emptyList()
}
