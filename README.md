# Rusuh Booking Tiket

## View Android analytics in DebugView

Install a staging debug build on a device or emulator, then run:

```sh
adb shell setprop log.tag.FA VERBOSE
adb shell setprop debug.firebase.analytics.app com.rusuh.app.android
```

Grant analytics consent in the app and watch `adb logcat` for `Logging event`. In Firebase Console, open Analytics >
DebugView and select the device. Setup for `google-services.json` and CI is in [Firebase distribution](docs/firebase-distribution.md).
