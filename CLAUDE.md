# CLAUDE.md — bkgapp

Android wallpaper app built on a fork of panels-art/WallApp (Kotlin Multiplatform, Compose UI on Android). Solo project: Khaled (designer, non-developer) + Claude Code. Read `docs/` before non-trivial work; `docs/02-TRD.md` §2 maps the repo. Current phase and per-phase briefs: `docs/06-implementation-plan.md` and `docs/tasks/`.

## Hard rules
- Android only. Never build or fix `app/ios`, `app/desktop`, `baselineprofile` unless asked.
- Minimal diff. Do not refactor, reformat, or "modernize" upstream code beyond the task. Log every upstream deviation in `CHANGES.md`.
- Do not bump Kotlin / AGP / Compose / Ktor / Koin versions unless the task is explicitly a dependency upgrade.
- Keep ViewModels and data layer untouched during UI work; UI tasks edit `shared/presentation/*` only.
- Never commit secrets: `google-services.json` (prod), keystores, `keystore.properties`, RevenueCat/AdMob keys → build config from gitignored properties.
- Remove any remaining "Panels", "MKBHD", "panels.art", "panels-oss" references when you meet them; do not add new ones.

## Verify
```
./gradlew :app:android:assembleDebug
./gradlew :shared:app:app-adapter:test
```
Run the build after every change. If sync fails, say so — don't guess at fixes that change versions.

## Where things are
- Screens: `shared/presentation/wallapp-ui/.../*Screen.kt`; theme: `shared/presentation/theme-ui`
- Content models: `shared/data/content-network/.../model`; content loading: `shared/data/content`, `remoteendpoint`, `mediamap`
- Account/Firestore: `shared/data/account-*`; Remote Config: `shared/data/remoteconfig-*`
- Billing: `shared/data/purchase`, `billing`, `shared/domain/licensing-billing`; Ads: `shared/data/ads-*`
- Android shim/config: `app/android/android.gradle.kts`, `AndroidManifest.xml`
- Content export tool: `service/service-export-remoteapi`; Firebase rules: `firebase-backend/`

## Style
Talk to Khaled like a teammate: short, direct, working code, explain only what's non-obvious. He designs; explain Compose concepts when they block a design decision, not otherwise.
