# 02 — Technical Requirements (TRD)

## 1. Inherited stack (as of upstream commit, verified 2026-09-20)
| Layer | Tech | Version |
|---|---|---|
| Language | Kotlin (shared + Android), Swift (iOS — not built in v1) | Kotlin 2.0.21 |
| Build | Gradle KTS, multi-module, `tooling/` included build, `moduleflavors.gradle` (flavor `wallApp`) | AGP 8.7.2 |
| UI (Android) | Jetpack Compose via Compose Multiplatform | CMP 1.7.1 / UI 1.7.5 |
| Navigation | Precompose (custom `ScreenNavigator` abstraction) | 1.6.0 |
| DI | Koin | 3.5.6 |
| Network | Ktor client + kotlinx.serialization | Ktor 2.3.12 |
| Images | Coil | 2.7.0 |
| Backend | Firebase Auth, Firestore, Storage, Remote Config, Crashlytics, Cloud Messaging | BoM 33.5.1 |
| Billing | RevenueCat (`shared/data/purchase`, `billing`, `licensing-billing`) | check `libs.versions.toml` |
| Ads | AdMob native + rewarded, Google UMP consent | — |
| Anim | Lottie | — |
| Perf | Baseline profiles (`baselineprofile/`) | — |
| Backend tooling | `service/service-export-remoteapi` (JVM app, firebase-admin + google-cloud-storage) uploads API data to Storage | — |

Codebase size: > 130k LOC. ~85 % Kotlin. ViewModel system is custom (predates KMP ViewModel); no pagination; known warts listed in `doc/faq.md`.

## 2. Repo map (what matters for us)
```
app/android            Android shim (applicationId, google-services.json, manifest, flavors)
shared/presentation/   Compose UI: wallapp-ui (screens), theme-ui (AppTheme, shapes, dynamic theme),
                       account-ui, remotepaywall-ui, image-ui, pixel-ui, compose-toolbox
shared/data/           content-network (JSON models), content (repo/cache), account-* (Firestore user),
                       remoteconfig-*, ads-admob, purchase/billing (RevenueCat), search-*, wallpaper,
                       remoteapi/remoteendpoint (spec.json, versioned endpoints), deeplink
shared/domain/         content-state, license-state, ads-support, billing-support, remotepaywall-support
shared/core/           network, download, security, setting, viewmodel, resource, monitoring
shared/di/             Koin modules; di-buildconfig-{debug,release}
shared/app/            app-adapter (most unit tests), app-initializer
service/               export tool: local API folder → process → upload to Firebase Storage
firebase-backend/      firestore.rules, storage.rules, indexes, rules tests (npm)
demo-assets/           reference export of the demo API data + gen_wallpapers.py
script/                build_debug, test_unit, ios_build
```

## 3. Architecture decisions for bkgapp
| # | Decision | Rationale |
|---|---|---|
| A1 | Keep KMP structure, build **Android only** | Rewriting to plain Android would cost more than living with KMP; iOS becomes cheap later |
| A2 | Do not delete `app/ios`, `app/desktop` — exclude from build/CI | Zero cost, keeps the option |
| A3 | Do not upgrade Kotlin/AGP/Compose in phase 1 | Get the fork compiling and rebranded first; upgrade in a dedicated phase with tests green |
| A4 | Keep Firebase for Auth/Firestore/RC/Crashlytics; Storage for content JSON + images (v1) | Matches upstream exactly, least risk. Images can move to a CDN later via `image_host_name` RC key |
| A5 | Single artist, but keep artist/folder/collection model unchanged | Data model is generic; removing artist concept touches too much UI |
| A6 | Content authored as files in a private `content/` repo folder → exported with `service-export-remoteapi` | Same pipeline Panels used; no admin backend to build |
| A7 | Keep Precompose nav + custom ViewModels | Migrating to Navigation Compose / androidx ViewModel is a refactor, not a v1 need |
| A8 | Product flavor renamed from `wallApp` to our brand only if cheap; otherwise keep | Flavor name is internal |

## 4. Rebranding checklist (code-level)
- `app/android/android.gradle.kts`: `applicationId` (currently `com.example.wallapp`), `versionCode/Name`, signing config (release keystore — **do not** ship `debug.keystore`).
- `google-services.json` → new Firebase project. Replace all `"panels-oss"` occurrences with the new project id.
- `GoogleSignInFactory.kt` `requestIdToken` → Web client ID of the new Firebase project.
- App name / launcher icon / splash / Lottie assets / `OssLicenses.kt` (regenerate or keep).
- Strings: grep `Panels`, `MKBHD`, `Marques`, `panels.art` → remove.
- Deep link host (`shared/data/deeplink`) → our domain + `assetlinks.json`.
- AdMob app id + unit ids (manifest meta-data + `ads-admob` module).
- RevenueCat API key (Play) + entitlement ids + product ids.
- Remote Config defaults (`shared/data/remoteconfig-*` defaults) → sane values for launch (see 05).

## 5. Content pipeline
1. Design in Illustrator/Figma → export **PNG master** at 2× the target phone resolution (e.g. 2880×6400 for 1440×3200 targets; at minimum 1440×3200). Keep a safe zone for clock/widgets.
2. Author metadata (wallpaper id, label, collection, tags/slugs, isDark, free flag, artist).
3. Run `service-export-remoteapi` with a `RemoteApiExportSpec` (currently only Staging is wired in `RemoteApiExport.main.kt`; add Prod spec). It processes the API root folder (`processData = true`) and uploads to Storage (`uploadData = true`). **Investigate `RemoteApiStorageManager` + `Constant.kt` first** — the exact input folder format and how variants (`p~s`, `p~uhd`, `t~m`…) and blurhashes are generated is not documented upstream; the FAQ says the original generator wasn't open-sourced, so we may need to write a small Python script that produces the folder layout in `demo-assets/api/99999999/` (that folder is a real export and is our reference).
4. App reads `spec.json` → `content-<v>` (all wallpapers/categories/artists/folders), `content-metadata-<v>` (search index), media under `media-<v>-{i|c}-<bucket>`.
5. Versioning: bump the API version folder for breaking changes; same folder for additive content.

Image formats: upstream demo uses PNG (slow). Target WebP (lossy q≈85 for previews, lossless or high-q for HD download). Check the media loader accepts WebP — it should (Coil), but verify the export tool doesn't assume PNG.

## 6. Build / verify commands
```
./gradlew :app:android:assembleDebug     # Android debug build
./gradlew :shared:app:app-adapter:test   # unit tests (run on JVM/desktop target)
./script/test_unit
./gradlew :app:android:installDebug      # to device
```
Exclude iOS/desktop from any CI. `settings.gradle.kts` has an `excludeAndroidModule` property for baselineprofile; check whether a similar switch exists for `:app:desktop` — if not, leave it, it only builds when invoked.

## 7. Dev environment (Android only)
- Any OS (Mac not required — the "A Mac" prerequisite in `doc/setup.md` is for iOS). Apple Silicon Mac → download the "Apple chip" build.
- **Android Studio** (upstream verified with Otter 2025.2.2; newer should be fine, if sync fails try that exact version). Bundled JDK 17 is enough.
- Git client (GitHub Desktop or CLI). Node 20+ for Firebase CLI + rules tests. Python 3 + Pillow if we write a content script.
- Android device with USB debugging, or an emulator (Pixel 8, API 34).
- Accounts to create: Firebase, Google Play Console ($25 one-time), AdMob, RevenueCat (free tier).

## 8. Risks
| Risk | Mitigation |
|---|---|
| Fork doesn't build on current Android Studio / Gradle | Pin AS version from setup.md; don't touch versions until it builds |
| Export tool undocumented input format | Reverse-engineer from `demo-assets/api/99999999` + `RemoteApiStorageManager` before designing 60 wallpapers |
| Firebase Storage egress cost + slow images | WebP + sized variants; CDN migration path via `image_host_name` |
| Play review: ads + subscriptions + account deletion | Follow checklist in 06 phase 7 |
| 130k LOC, solo non-dev | Change as little as possible; keep a `CHANGES.md` of every upstream deviation to survive future merges |
