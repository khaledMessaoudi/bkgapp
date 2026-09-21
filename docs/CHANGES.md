# CHANGES — deviations from upstream

Every change to code inherited from [panels-art/WallApp](https://github.com/panels-art/WallApp) goes here, newest first. Tag `v0.0.0-upstream` marks the untouched fork.

Format: `- <date> · <area> — <what and why> (<commit or file>)`

## Deviations
- 2026-09-21 · content delivery — added `shared/core/download/.../HttpApiDownloader.kt` (new file) and a
  four-line branch in `shared/di/di-base/.../Factory.android.kt` `firebaseStorageDownloader()`: when
  `CONTENT_HTTP_BASE_URL` is set, the encrypted content API is fetched over plain HTTPS instead of
  Firebase Cloud Storage. Reason: Cloud Storage for Firebase requires the paid Blaze plan as of
  2026-02-03 and this project runs card-free on Spark. The `api/<version>/` paths are already
  relative, so `tools/content_build.py` output is unchanged. Default is blank — upstream Firebase
  Storage behaviour — so nothing changes until a host is configured.
- 2026-09-21 · content delivery — added `shared/core/download/.../FirestoreKeyApiDownloader.kt` (new
  file) and `content_config/{document}` to `firebase-backend/firestore.rules`: the content API
  encryption key is read from Firestore rather than served next to the encrypted content, so it
  stays readable only by a signed-in user — the guarantee upstream got from its Storage rules.
  Wrapped around `HttpApiDownloader` in `Factory.android.kt` when `CONTENT_KEY_FROM_FIRESTORE` is
  true. The document holds the obfuscated key verbatim, so `RemoteApiSecretManager` is unchanged.
  Added `gitlive.firebase.firestore` to `shared/core/download/download.gradle.kts`, and three rules
  tests in `firebase-backend/test/test.js` (57 passing).
  **Open:** the content export still has to publish the key to `content_config/encryption` —
  `tools/` has no step for that yet.
- 2026-09-21 · performance — removed `android:largeHeap="true"` from `app/android/src/main/AndroidManifest.xml` and lowered the Coil memory cache from 50% to 25% of the app memory budget in `shared/di/di-base/.../Factory.android.kt`. The app was using 400+ MB on a Samsung. Trade-off: previously viewed wallpapers reload from disk more often.
- 2026-09-20 · security — added `shared/core/security/src/commonTest/.../PythonCiphertextCompatTest.kt`, pinning the AES-GCM ciphertext layout that `tools/encrypt_api.py` produces. New test file, no upstream code touched. Run with `./gradlew :shared:core:security:desktopTest`.
- 2026-09-20 · **uncommitted, local only** — `RUN_FIREBASE_ON_LOCAL_EMULATORS` in `shared/data/account-api/.../AccountManager.kt` must be flipped to `true` to run against the Storage emulator. Deliberately not committed: `true` would break builds pointing at real Firebase. See `tools/README.md`.

## Non-deviations (ours, not upstream code)
- 2026-09-20 · build — reduced Gradle/Kotlin daemon heap in `gradle.properties` to fit available RAM (`eda9372`). Local build config, not app behaviour.
- 2026-09-20 · docs — added `CLAUDE.md` and `docs/` (`ccd669b`).
- 2026-09-20 · tools — added `tools/content_build.py` + `tools/validate_content.py`, our own content pipeline. New files only; `service/service-export-remoteapi` is untouched and still does the encrypt+upload step.
