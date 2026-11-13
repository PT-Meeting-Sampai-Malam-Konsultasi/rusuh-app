# iOS flavors

iOS now has two schemes that mirror the Android product flavors: **Rusuh Staging** and **Rusuh Production**.
Both share the same bundle identifier (`com.rusuh.app`) and differ only in the display name and debug options.

## Schemes and configurations

| Scheme | Build configuration | Environment | Debug options | Display name |
|---|---|---|---|---|
| `Rusuh Staging` | `Debug-Staging` | Staging | Yes | Rusuh Staging |
| `Rusuh Staging` | `Release-Staging` | Staging | Yes | Rusuh Staging |
| `Rusuh Production` | `Debug-Production` | Production | No | Rusuh |
| `Rusuh Production` | `Release-Production` | Production | No | Rusuh |

The staging scheme allows debug builds to switch backend modes (`MOCK_API=true`, `BACKEND_MODE=Stub`). The
production scheme ignores these variables in any build type.

Release builds (any scheme) always use the production API.

## Build settings

The environment settings are exposed as Xcode build settings:

| Setting | Staging | Production |
|---|---|---|
| `RUSUH_ENVIRONMENT` | `Staging` | `Production` |
| `RUSUH_ALLOW_DEBUG_OPTIONS` | `YES` | `NO` |
| `RUSUH_APP_DISPLAY_NAME` | `Rusuh Staging` | `Rusuh` |
| `KOTLIN_FRAMEWORK_BUILD_TYPE` | `Debug`/`Release` | `Debug`/`Release` |

`KOTLIN_FRAMEWORK_BUILD_TYPE` is set at the project level (Debug configurations = Debug, Release configurations =
Release) so the Gradle build phase picks the right KMP framework variant.

## Info.plist

Both plists read the build settings via variable expansion:

```xml
<key>RusuhEnvironment</key>
<string>$(RUSUH_ENVIRONMENT)</string>
<key>RusuhAllowDebugOptions</key>
<string>$(RUSUH_ALLOW_DEBUG_OPTIONS)</string>
<key>CFBundleDisplayName</key>
<string>$(RUSUH_APP_DISPLAY_NAME)</string>
```

At runtime, Swift reads these values and passes them to the Kotlin entry point (`KoinIosKt.doInitKoin`).

## Swift entry point

`iOSApp.swift` reads the Info.plist keys and decides which backend to use:

```swift
let environmentName = infoPlist["RusuhEnvironment"] as? String ?? "Staging"
let allowDebugOptions = (infoPlist["RusuhAllowDebugOptions"] as? String) == "YES"

#if DEBUG
KoinIosKt.doInitKoin(
    isDebug: true,
    environmentName: environmentName,
    allowDebugOptions: allowDebugOptions,
    backendModeName: allowDebugOptions ? environment["BACKEND_MODE"] : nil,
    useMockServer: allowDebugOptions && environment["MOCK_API"] == "true"
)
#else
KoinIosKt.doInitKoin(
    isDebug: false,
    environmentName: environmentName,
    allowDebugOptions: false,
    backendModeName: nil,
    useMockServer: false
)
#endif
```

## CI

The iOS workflow (`ios-build.yml`) builds both schemes:

1. **Staging**: archived via Fastlane (`build_unsigned` lane), uploaded as artifact on pushes to main and release/** only
2. **Production**: simulator build via Fastlane (`build_production_simulator` lane) for verification

## Comparison with Android

| Aspect | Android | iOS |
|---|---|---|
| Flavor dimension | `environment` in Gradle | Build configuration in Xcode |
| Staging flavor | `staging` product flavor | `Rusuh Staging` scheme |
| Production flavor | `production` product flavor | `Rusuh Production` scheme |
| Bundle ID | Same (`com.rusuh.app.android`) | Same (`com.rusuh.app`) |
| Display name | `resValue` in Gradle | `CFBundleDisplayName` in Info.plist |
| Environment config | `BuildConfig.ENVIRONMENT` | `RusuhEnvironment` plist key |
| Debug options config | `BuildConfig.ALLOW_DEBUG_OPTIONS` | `RusuhAllowDebugOptions` plist key |
