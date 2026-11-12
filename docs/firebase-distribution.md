# Firebase App Distribution (Android demo builds)

The `distribute` job in `.github/workflows/android-build.yml` sends a debug APK to testers on every push to `main` and on
manual dispatch (Actions > Android Build > Run workflow, input `backend`):

- **live** (default for pushes): `-PmockApi=false`, the app talks to the staging API `https://api.rusuh-tiket.web.id/v1`.
  Home events and login work today; Detail, Tickets and the queue still use stub data until the backend is complete
  (the release note says so). Use this build to retest as the backend routes appear.
- **stub**: `-PbackendMode=Stub`, an offline demo with the in-process stub backend (no network needed). The Mockoon
  address `10.0.2.2` only works on the emulator, so a mock build is not distributed.

Every build gets `versionCode` = the workflow run number (`-PversionCode`), so testers can tell builds apart.
iOS is not distributed: a device install needs a signed build and the paid Apple Developer Program.

## One-time setup
1. Firebase project `rusuh-app` with the Android app `com.rusuh.app.android`; App Distribution enabled.
2. A tester group. The workflow uses the alias `rusuh-devs`; to use another alias set the repository variable
   `FIREBASE_TESTER_GROUPS` (comma separated aliases). The alias is shown under App Distribution > Testers & Groups.
3. A Google Cloud service account for project `rusuh-app` with the role **Firebase App Distribution Admin** and a JSON key.
4. In Settings > Secrets and variables > Actions:
   - secret `FIREBASE_SERVICE_ACCOUNT_JSON`: the whole JSON key;
   - `FIREBASE_APP_ID`: the Android app id (`1:...:android:...`, Firebase Project settings > Your apps). It is not a
     secret: a repository **variable** is enough (a secret with the same name also works; the variable wins).
Until both exist the job only prints a notice and passes.

## Notes
- `google-services.json` is **not** needed (no Firebase SDK in the app) and must not be committed (public repo).
- The key file is written to the runner's temp dir with owner-only permissions and removed when the step ends.
- Rotate the key: create a new JSON key for the service account, update the secret, delete the old key in Google Cloud.
- Release notes carry the short commit id and the first line of the commit message.
