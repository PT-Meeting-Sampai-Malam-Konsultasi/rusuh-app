package com.rusuh.app.feature.eticket

/**
 * Result of attempting to launch the calendar.
 */
sealed interface CalendarLaunchResult {
    /** Calendar app was launched successfully with the event data. */
    data object Success : CalendarLaunchResult

    /** No calendar app available to handle the request. */
    data object NoCalendarApp : CalendarLaunchResult
}

/**
 * Platform abstraction for adding events to the device calendar.
 * Implementations are bound per platform in Koin: Android uses CalendarContract intents,
 * iOS uses EventKitUI or a share sheet.
 */
interface CalendarLauncher {
    /**
     * Opens the device calendar to add the given event. The user confirms the addition
     * in the calendar app; this method does not add the event directly.
     *
     * @param event The event data to pre-populate in the calendar.
     * @return The result of the launch attempt.
     */
    fun launch(event: CalendarEvent): CalendarLaunchResult
}
