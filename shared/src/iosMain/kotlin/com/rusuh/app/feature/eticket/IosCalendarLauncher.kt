package com.rusuh.app.feature.eticket

import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneWithName

/**
 * iOS implementation of [CalendarLauncher] using EventKit.
 *
 * This creates a calendar event and presents it via EKEventEditViewController, which lets
 * the user review and save the event. No calendar permission prompt is needed for the edit
 * view controller flow as the user explicitly chooses to save.
 *
 * Note: The actual presentation requires UIKit integration from the Swift side. This class
 * prepares the EKEvent; the Swift layer should present EKEventEditViewController. The iOS
 * implementation is best-effort and verified on the simulator by the user.
 */
class IosCalendarLauncher(
    private val presentEventEditor: (EKEvent, EKEventStore) -> Boolean,
) : CalendarLauncher {
    private val eventStore = EKEventStore()

    override fun launch(event: CalendarEvent): CalendarLaunchResult {
        val ekEvent = buildEKEvent(event)
        val presented = presentEventEditor(ekEvent, eventStore)
        return if (presented) CalendarLaunchResult.Success else CalendarLaunchResult.NoCalendarApp
    }

    private fun buildEKEvent(event: CalendarEvent): EKEvent {
        val ekEvent = EKEvent.eventWithEventStore(eventStore)
        ekEvent.title = event.title
        ekEvent.location = event.location
        ekEvent.startDate = NSDate.dateWithTimeIntervalSince1970(event.beginMillis / MILLIS_PER_SECOND)
        ekEvent.endDate = NSDate.dateWithTimeIntervalSince1970(event.endMillis / MILLIS_PER_SECOND)
        NSTimeZone.timeZoneWithName(event.timeZoneId)?.let { ekEvent.timeZone = it }
        return ekEvent
    }

    companion object {
        private const val MILLIS_PER_SECOND = 1000.0
    }
}
