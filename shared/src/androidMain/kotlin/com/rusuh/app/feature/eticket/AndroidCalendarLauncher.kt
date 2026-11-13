package com.rusuh.app.feature.eticket

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract

/**
 * Android implementation of [CalendarLauncher] using [CalendarContract.Events.CONTENT_URI].
 * Opens the device calendar app to add an event; no calendar permission is required as this
 * uses an ACTION_INSERT intent that lets the user confirm the addition.
 *
 * @param contextProvider Provides the current Android context (Activity preferred for proper task stack).
 */
class AndroidCalendarLauncher(
    private val contextProvider: () -> Context,
) : CalendarLauncher {
    override fun launch(event: CalendarEvent): CalendarLaunchResult {
        val context = contextProvider()
        val intent = buildCalendarIntent(event)

        // If context is not an Activity, need FLAG_ACTIVITY_NEW_TASK
        if (context !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            context.startActivity(intent)
            CalendarLaunchResult.Success
        } catch (_: ActivityNotFoundException) {
            CalendarLaunchResult.NoCalendarApp
        }
    }

    companion object {
        /**
         * Builds the calendar insert intent. Exposed for testing.
         */
        fun buildCalendarIntent(event: CalendarEvent): Intent =
            Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, event.title)
                putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
                putExtra(CalendarContract.Events.EVENT_TIMEZONE, event.timeZoneId)
            }
    }
}
