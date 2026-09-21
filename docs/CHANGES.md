# CHANGES — deviations from upstream

Every change to code inherited from [panels-art/WallApp](https://github.com/panels-art/WallApp) goes here, newest first. Tag `v0.0.0-upstream` marks the untouched fork.

Format: `- <date> · <area> — <what and why> (<commit or file>)`

## Deviations
- 2026-09-21 · performance — removed `android:largeHeap="true"` from `app/android/src/main/AndroidManifest.xml` and lowered the Coil memory cache from 50% to 25% of the app memory budget in `shared/di/di-base/.../Factory.android.kt`. The app was using 400+ MB on a Samsung. Trade-off: previously viewed wallpapers reload from disk more often.
- 2026-09-20 · security — added `shared/core/security/src/commonTest/.../PythonCiphertextCompatTest.kt`, pinning the AES-GCM ciphertext layout that `tools/encrypt_api.py` produces. New test file, no upstream code touched. Run with `./gradlew :shared:core:security:desktopTest`.
- 2026-09-20 · **uncommitted, local only** — `RUN_FIREBASE_ON_LOCAL_EMULATORS` in `shared/data/account-api/.../AccountManager.kt` must be flipped to `true` to run against the Storage emulator. Deliberately not committed: `true` would break builds pointing at real Firebase. See `tools/README.md`.

## Non-deviations (ours, not upstream code)
- 2026-09-20 · build — reduced Gradle/Kotlin daemon heap in `gradle.properties` to fit available RAM (`eda9372`). Local build config, not app behaviour.
- 2026-09-20 · docs — added `CLAUDE.md` and `docs/` (`ccd669b`).
- 2026-09-20 · tools — added `tools/content_build.py` + `tools/validate_content.py`, our own content pipeline. New files only; `service/service-export-remoteapi` is untouched and still does the encrypt+upload step.
