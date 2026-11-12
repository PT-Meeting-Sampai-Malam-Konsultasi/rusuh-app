# Runtime language switch

The selected language lives in `AppLanguageState`, backed by `AppPreferences` and exposed as a `StateFlow`. The app root provides it through `LocalAppLanguage`, applies the platform locale override, and keys the navigation tree by language so Compose resources are resolved again after a selection. Header toggles on the app screens update the same state. The active language also supplies `Accept-Language` on each API request.

On Android, the override updates `Locale.setDefault` and the app resource configuration before the keyed UI is composed; no restart is intended. On iOS, the implementation writes `AppleLanguages` to `NSUserDefaults`. Compose Multiplatform Resources 1.7.0 reads the preferred language through platform locale APIs; this process-level refresh could not be verified here. Treat iOS runtime switching as unverified: it may require an app restart.
