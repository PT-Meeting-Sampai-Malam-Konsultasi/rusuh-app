# UI testing

## Run tests

Shared screen tests run on the JVM with Robolectric and need no emulator:

```sh
./gradlew :shared:testDebugUnitTest
```

The Android smoke test is opt-in and requires a running emulator or device. With build flavors, the task name includes the flavor. For staging (the default development flavor), it launches the staging debug app with the Stub backend and walks through sign-in using the synthetic code `123456`:

```sh
./gradlew :androidApp:connectedStagingDebugAndroidTest -PbackendMode=Stub
```

For production flavor (testing production configuration without debug options):

```sh
./gradlew :androidApp:connectedProductionDebugAndroidTest
```

The connected tests are intentionally not part of CI. Build the test APKs without a device using `./gradlew :androidApp:assembleStagingDebugAndroidTest` or `./gradlew :androidApp:assembleProductionDebugAndroidTest`.

## Test task differences

With build flavors (staging and production), the task names include the flavor:

- `:shared:testDebugUnitTest` runs Android-specific unit tests in the `androidUnitTest` source set under Robolectric. This includes Compose UI tests that need Android resources and the Robolectric shadow framework. The shared module has no flavor dimension.
- `:androidApp:testStagingDebugUnitTest` runs Android app unit tests for the staging debug variant.
- `:androidApp:testProductionDebugUnitTest` runs Android app unit tests for the production debug variant.
- `:shared:allTests` runs tests from all source sets: `commonTest` (pure Kotlin tests that run on every target), plus each platform's test source set. On Android it invokes `testDebugUnitTest`; on JVM/Native it runs the corresponding test tasks.

In practice, `allTests` is a superset. CI runs both to ensure common tests pass on all targets and Android-specific tests pass with Robolectric.

## Screen test pattern

Prefer the stateless screen overloads: pass a concrete UI state and no-op or recording callbacks, then assert visible copy and accessibility semantics. Reuse preview fixtures such as `homePreviewEvents()` and `sampleETicketOrder()` where they represent the case; otherwise use small synthetic values. Do not start view models or make HTTP requests in these tests. Keep assertions focused on user-visible behavior such as headings, button enabled state, and content descriptions.

The first suite covers sign-in steps, the Home event list, signed-out Tiket, and e-ticket content and error rendering. Next, cover the Account profile states, My Queues states, loading and retry behavior, locale selection, and action dispatch callbacks.

## Test setup and isolation

Every screen test class follows the same pattern for Koin setup and teardown:

```kotlin
@Before
fun initializeTestEnvironment() {
    initializeComposeResourceContext()
    startScreenTestKoin()
}

@After
fun tearDown() {
    stopKoin()
}
```

`initializeComposeResourceContext()` sets the Android context required by Compose Multiplatform resources. `startScreenTestKoin()` starts a minimal Koin context with only the dependencies needed by screen tests (currently just `AppLanguageState`). The `@After` teardown ensures Koin is stopped after each test, preventing state leakage between tests.

Note: `startScreenTestKoin()` calls `stopKoin()` before starting to handle cases where a previous test left Koin running, but explicit `@After` teardown is still needed for proper isolation and clarity.

## Robolectric configuration

`shared/src/androidUnitTest/resources/robolectric.properties` configures Robolectric:

```properties
sdk=34
```

Configuration choices:

- **sdk=34**: Matches the app's `targetSdk`. Robolectric shadows Android 34 APIs, ensuring tests run against the same behavior as production.
- **unitTests.isIncludeAndroidResources=true**: Set in `shared/build.gradle.kts`, this enables Robolectric to load Android resources including Compose Multiplatform resources packaged as assets.
- **No qualifiedResources override**: The default behavior loads resources correctly. If issues emerge with resource loading on Robolectric upgrades, add `qualifiedResources=org.robolectric.QualifiedResources.DISABLED`.
- **No application class**: Tests use the default `Application`. If a custom test application is needed later, add `application=com.rusuh.app.TestApplication` and create that class.

## Robolectric notes

`shared` enables `unitTests.isIncludeAndroidResources` and uses SDK 34. Compose Multiplatform resources are packaged as Android assets by the shared Android target; these tests render screens using `Res.string` through the generated resource loader, so a missing asset surfaces immediately during composition. The instrumentation test uses the existing debug-only `-PbackendMode=Stub` property; no app code or release configuration is changed for testing.
