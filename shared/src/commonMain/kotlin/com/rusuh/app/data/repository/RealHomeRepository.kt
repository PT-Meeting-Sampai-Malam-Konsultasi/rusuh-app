package com.rusuh.app.data.repository

import com.rusuh.app.data.clock.parseStringDateMillis
import com.rusuh.app.data.network.LocaleProvider
import com.rusuh.app.data.remote.EventApiService
import com.rusuh.app.data.remote.mapper.toHomeEventItem
import com.rusuh.app.domain.clock.ServerClock
import com.rusuh.app.domain.model.DomainException
import com.rusuh.app.domain.model.DomainResult
import com.rusuh.app.feature.home.HomeEventItem
import com.rusuh.app.feature.home.HomeRepository

/**
 * [HomeRepository] over `GET /core/events`. The countdown on coming-soon cards counts from the response's `serverNow`
 * (the [serverClock] when the body has none and it is synced), and all copy follows the active language of
 * [localeProvider].
 * A failed call throws [DomainException], which the ViewModel shows as its error state with a retry.
 *
 * Cache-first rendering (`HomeCache`) is not wired yet.
 */
class RealHomeRepository(
    private val api: EventApiService,
    private val localeProvider: LocaleProvider,
    private val serverClock: ServerClock,
) : HomeRepository {
    override suspend fun getEvents(): List<HomeEventItem> {
        val result = api.getEvents().toDomainResult()
        if (result !is DomainResult.Success) throw DomainException(result)
        val body = result.data
        val language = localeProvider.language()
        // An unsynced clock counts from an arbitrary start: without a server time the cards show no countdown.
        val now = body.serverNow?.let(::parseStringDateMillis) ?: serverClock.takeIf { it.isSynced }?.nowMillis()
        return body.items.mapNotNull { it.toHomeEventItem(language, now) }
    }
}
