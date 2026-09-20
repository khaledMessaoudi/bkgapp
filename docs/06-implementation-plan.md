# 06 — Implementation plan

Rule of the project: **change as little upstream code as possible**. Every deviation goes in `CHANGES.md`. Each phase ends with `./gradlew :app:android:assembleDebug` green and the app running on a device.

## Phase 0 — Environment (½ day) ✅ 2026-09-20
- [ ] Install Android Studio (Otter 2025.2.2 known-good), GitHub Desktop or git CLI, Node 20, Firebase CLI (`npm i -g firebase-tools`).
- [ ] Clone fork: `git clone https://github.com/khaledMessaoudi/bkgapp.git`, add upstream: `git remote add upstream https://github.com/panels-art/WallApp.git`.
- [ ] Emulator Pixel 8 API 34 or physical device with USB debugging.
- [ ] Copy `docs/` and `CLAUDE.md` from this pack into the repo root, commit.

## Phase 1 — Build the fork untouched (½–1 day) ✅ 2026-09-20 (runs in emulator; tag pending)
- [ ] Open project in Android Studio → Gradle sync → view "Project" → run config `app.android`, flavor `wallApp`.
- [ ] `./gradlew :app:android:assembleDebug` → install → app runs with bundled demo content (upstream says the OSS build supports bundled API data).
- [ ] `./gradlew :shared:app:app-adapter:test` green.
- [ ] Tag `v0.0.0-upstream`.
Exit: demo app runs on your phone. Do not proceed until this works.

## Phase 2 — Understand the content pipeline (1–2 days, mostly reading) ← current · brief: `docs/tasks/phase-2-content-pipeline.md`
- [x] Read `service/service-export-remoteapi/*` (`Constant.kt`, `RemoteApiStorageManager.kt`, `RemoteApiExportSpec.kt`) and `shared/data/remoteendpoint`, `mediamap*`, `FirebaseStorageDownloader` implementations.
- [x] Fill in the TBDs in `05-backend-schema.md` §3: exact media bucket sizes, what `c` kind is, how the app resolves `spec.json`, whether the tool generates variants + blurhash or expects them pre-made.
- [x] Decide: **`tools/content_build.py`** (Pillow + blurhash), built and self-tested; the Kotlin export tool stays untouched as the encrypt+upload step. See `tools/README.md`.
- [ ] Prototype with 3 of your own wallpapers → run through the tool → load in the app from local/bundled data.
Exit: your own 3 wallpapers show in the demo app.

## Phase 3 — Rebrand + own Firebase (1–2 days)
- [ ] Create Firebase projects `bkgapp-dev`, `bkgapp-prod`; enable Auth (Anonymous, Google), Firestore, Storage, RC, Crashlytics.
- [ ] Deploy rules: `cd firebase-backend && firebase deploy --only firestore:rules,storage` (after `firebase use`). Run rules tests (`firebase-backend/test`, `npm test` with emulators).
- [ ] `applicationId` → final package; `google-services.json`; replace `panels-oss`; `GoogleSignInFactory.kt` web client id; SHA-1/256 of debug + release keystores registered in Firebase.
- [ ] Generate release keystore (keep out of git; `keystore.properties` gitignored).
- [ ] App name, launcher icon (adaptive), splash, remove Panels/MKBHD strings and images, `doc/img` screenshots.
- [ ] Upload phase-2 API data to Storage (dev) with the export tool; point app at it; confirm remote loading works and anon auth gates Storage.
Exit: rebranded app pulling your content from your Firebase.

## Phase 4 — Monetization wiring (1–2 days)
- [ ] Play Console app created (needed for RevenueCat + real product ids). Internal testing track.
- [ ] Products/base plans in Play; RevenueCat project, entitlement `premium`, offering `default`; API key in build config (debug/release).
- [ ] AdMob app + native + rewarded units; UMP messages; test device ids in debug.
- [ ] RC defaults set per 05 §5; publish RC in dev project.
- [ ] Test on internal track with license testers: subscribe, restore, cancel, reward-ad unlock, feed ads, GDPR form.
Exit: full free → ad → paywall → subscribe loop works on a test account.

## Phase 5 — UI redesign (2–4 weeks, iterative)
Order by impact / risk:
1. Tokens: `theme-ui` colors, type, shapes to match Figma. Fonts as Compose resources.
2. Cards + grids (wallpaper card, collection card, folder banner) and any media-bucket changes → re-export content if ratios change.
3. Wallpaper detail + action sheet + set-wallpaper flow.
4. Home/Explore composition, bottom bar.
5. Paywall template (remote config'd), reward-ad dialog.
6. FirstRun/onboarding, Profile/Settings, empty/error states.
7. Motion.
Work screen by screen with Claude Code: "open `CollectionScreen.kt`, here is the Figma spec (tokens + measurements), keep the ViewModel untouched."
Exit: design sign-off on device, light + dark.

## Phase 6 — Content production (parallel with 5)
- [ ] ≥ 60 wallpapers, ≥ 5 collections, folders for Home, highlights chosen, `free` flags, slugs for search.
- [ ] Export → Storage prod. Size audit: thumbs < 150 KB, HD < 3 MB.
- [ ] Privacy policy + terms pages, deep-link domain + `assetlinks.json`.

## Phase 7 — Release hardening (1 week)
- [ ] Trim upstream leftovers: iOS/desktop out of CI, showcase screens debug-only, `followingIds`/Connections hidden if unused, OSS licenses list regenerated.
- [ ] Account deletion end-to-end; data safety form; ads declaration; subscription disclosures in listing.
- [ ] Crashlytics wired in release; baseline profile generated (`baselineprofile/`).
- [ ] Release build, R8/ProGuard check (`proguard-rules.pro`), install from AAB on 2–3 devices (low-end included).
- [ ] Store listing: screenshots, feature graphic, description. Closed testing (Play requires 12 testers / 14 days for new personal accounts — verify current policy) → production.

## Phase 8 — Post-launch
- Content cadence, RC-driven highlights, analytics review, consider CDN for images, dependency upgrade sprint (Kotlin/AGP/Compose), then iOS.

## Working with Claude Code
- Always start a task with the file paths from `03-app-flow.md` / `02-TRD.md` §2.
- One screen or one module per session; run the build command at the end of every task.
- Never let it "clean up" upstream code opportunistically — merge pain later.
