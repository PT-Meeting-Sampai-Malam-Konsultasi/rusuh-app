package com.rusuh.app.domain.repository

import com.rusuh.app.domain.model.DomainResult

/** The anonymous identity of this install: a signed device token the queue endpoints require. */
interface DeviceRepository {
    /**
     * Makes sure a valid device token is stored. Does nothing when one exists and has not expired; otherwise
     * registers the install and stores the token. [forceRefresh] registers even then, for a caller that just got a
     * 401 on a device-token call (do it once, not in a loop). 400, 429 and 503 come back as
     * [DomainResult.Error], transport problems as [DomainResult.NetworkError] or [DomainResult.TechnicalError].
     */
    suspend fun ensureRegistered(forceRefresh: Boolean = false): DomainResult<Unit>
}
