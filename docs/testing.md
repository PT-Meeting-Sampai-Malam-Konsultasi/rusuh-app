# UI testing

## Run tests

Shared screen tests run on the JVM with Robolectric and need no emulator:

```sh
./gradlew :shared:testDebugUnitTest
```

The Android smoke test is opt-in and requires a running emulator or device. It launches the debug app with the Stub backend and walks through sign-in using the synthetic code `123456`:

```sh
./gradlew :androidApp:connectedDebugAndroidTest -PbackendMode=Stub
```

The connected test is intentionally not part of CI. Build its APK without a device using `./gradlew :androidApp:assembleDebugAndroidTest`.

## Screen test pattern

Prefer the stateless screen overloads: pass a concrete UI state and no-op or recording callbacks, then assert visible copy and accessibility semantics. Reuse preview fixtures such as `homePreviewEvents()` and `sampleETicketOrder()` where they represent the case; otherwise use small synthetic values. Do not start view models or make HTTP requests in these tests. Keep assertions focused on user-visible behavior such as headings, button enabled state, and content descriptions.

The first suite covers sign-in steps, the Home event list, signed-out Tiket, and e-ticket content and error rendering. Next, cover the Account profile states, My Queues states, loading and retry behavior, locale selection, and action dispatch callbacks.

## Robolectric notes

`shared` enables `unitTests.isIncludeAndroidResources` and uses SDK 34. Compose Multiplatform resources are packaged as Android assets by the shared Android target; these tests render screens using `Res.string` through the generated resource loader, so a missing asset surfaces immediately during composition. The instrumentation test uses the existing debug-only `-PbackendMode=Stub` property; no app code or release configuration is changed for testing.
