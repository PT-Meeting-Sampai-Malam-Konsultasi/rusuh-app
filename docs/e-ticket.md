# E-Ticket Feature

The e-ticket page displays issued tickets for a paid order, including QR codes for venue entry and an "Add to calendar" button.

## Add to Calendar (FR-22-M6)

The "Tambah ke kalender" / "Add to calendar" button opens the device calendar app to add the event.

### Architecture

```
ETicketOrder  →  CalendarEvent  →  CalendarLauncher (platform)  →  Device Calendar
```

- **CalendarEvent** (`CalendarEvent.kt`): Pure data class with event details (title, location, begin/end times in epoch millis, time zone ID).
- **CalendarLauncher** (`CalendarLauncher.kt`): Interface with `launch(event): CalendarLaunchResult` abstracting platform differences.
- **ETicketOrder.toCalendarEvent()**: Maps the e-ticket order to a calendar event. Uses doors-open time as the start with a 3-hour default duration.

### Platform Implementations

#### Android (`AndroidCalendarLauncher`)

Uses `Intent.ACTION_INSERT` with `CalendarContract.Events.CONTENT_URI`:
- No calendar permission required (user confirms in calendar app)
- Extras: `TITLE`, `EVENT_LOCATION`, `EXTRA_EVENT_BEGIN_TIME`, `EXTRA_EVENT_END_TIME`, `EVENT_TIMEZONE`
- Handles `ActivityNotFoundException` gracefully, returning `NoCalendarApp`
- Adds `FLAG_ACTIVITY_NEW_TASK` when not launched from an Activity context

#### iOS (`IosCalendarLauncher`)

Uses EventKit (`EKEvent` and `EKEventStore`):
- Presenter callback (`iosCalendarPresenter`) must be set from Swift to present `EKEventEditViewController`
- Until the presenter is set, returns `NoCalendarApp`
- No permission prompt needed for the edit view controller flow (user explicitly saves)
- **Note**: iOS implementation is best-effort; verified on simulator by the user

### Time Zone Mapping

Indonesian time zones map to standard identifiers:
- WIB → `Asia/Jakarta` (UTC+7)
- WITA → `Asia/Makassar` (UTC+8)  
- WIT → `Asia/Jayapura` (UTC+9)

### MVI Flow

```
User taps button  →  AddToCalendar intent  →  ViewModel calls CalendarLauncher
                                                    ↓
                                          Success: LaunchCalendar effect
                                          Failure: NoCalendarApp effect
```

### Strings

| Key | EN | ID |
|-----|----|----|
| `eticket_add_to_calendar` | Add to calendar | Tambah ke kalender |
| `eticket_add_to_calendar_description` | Add event to calendar | Tambahkan acara ke kalender |
| `eticket_no_calendar_app` | No calendar app available | Tidak ada aplikasi kalender |

### Accessibility

The calendar button has a content description (`eticket_add_to_calendar_description`) for screen readers.

### Testing

#### Unit Tests (commonTest)
- `CalendarEventTest`: Tests time zone mapping, event data extraction for WIB/WITA/WIT, midnight edge case, missing doors-open time

#### Android Tests (androidUnitTest, Robolectric)
- `AndroidCalendarLauncherTest`: Verifies intent action, URI, and all extras
- `ETicketScreenTest.calendarButtonClickCallsOnAddToCalendar`: UI test verifying button click invokes callback

### Dependencies

- **Android**: Uses standard `CalendarContract` APIs (no new dependencies)
- **iOS**: Uses `EventKit` framework (part of iOS SDK)

### Known Limitations

1. iOS presenter callback must be wired from Swift (`iosCalendarPresenter` in `PlatformModule.ios.kt`)
2. No toast/snackbar shown on `NoCalendarApp` yet (logged for future enhancement)
3. Event duration is fixed at 3 hours (no API field for actual end time)
