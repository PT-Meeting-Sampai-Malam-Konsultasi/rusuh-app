# Runtime language switch

The selected language lives in `AppLanguageState`, backed by `AppPreferences` and exposed as a `StateFlow`. The app root provides it through `LocalAppLanguage`, applies the platform locale override synchronously, and keys the navigation tree by language so Compose recomposes with fresh resource lookups. Header toggles on the app screens update the same state, including sign-in. The active language also supplies `Accept-Language` on each API request, and the stored choice is restored at startup.

## Platform language codes and resource folders

Indonesian has different language codes on Android and iOS:

| Platform | API | Returns for Indonesian | Resource folder |
|---|---|---|---|
| Android | `java.util.Locale.getLanguage()` | `"in"` (legacy ISO 639-1) | `values-in/` |
| iOS | `NSLocale.languageCode` | `"id"` (BCP 47) | `values-id/` |

Compose Multiplatform Resources (CMP 1.7.0) uses `Locale.current.language` from `androidx.compose.ui.text.intl`:
- On Android, this delegates to `android.os.LocaleList.getDefault()`, which returns the legacy `"in"` code.
- On iOS/macOS, this reads `NSLocale.languageCode` directly, returning the BCP 47 `"id"` code.

CMP then looks for a resource folder matching the language code. To ensure Indonesian strings load on both platforms, we maintain **two Indonesian resource folders** with identical content:
- `values-id/strings.xml` — for iOS resource resolution
- `values-in/strings.xml` — for Android resource resolution

A unit test (`LanguageStringParityTest.indonesianAndroidAndIosFoldersHaveIdenticalContent`) verifies that both folders have identical keys and values. When adding or changing Indonesian strings, edit one file and copy to the other, or the test fails.

## Android

On Android, `applyPlatformLanguage` calls `android.os.LocaleList.setDefault()` before composition, ensuring that `androidx.compose.ui.text.intl.Locale.current` returns the selected locale when CMP Resources resolves `stringResource`/`pluralStringResource`.

## iOS

On iOS, `applyPlatformLanguage` writes the language tag (`"id"` for Indonesian) to the `AppleLanguages` key in `NSUserDefaults`. CMP Resources reads `NSLocale.languageCode`, which returns `"id"` for Indonesian, and resolves to `values-id/strings.xml`.

Note: The `AppleLanguages` write affects the next app launch. Immediate runtime switching on iOS is best effort; a full resource refresh may require an app restart. The `key(language)` wrapper in `App.kt` forces recomposition, which re-reads resources and typically updates visible strings without restart.

## Accept-Language header

The app sends `Accept-Language: id` (BCP 47) for Indonesian regardless of platform. The `AppLanguage.tag` property always returns the BCP 47 code, which the backend expects.
