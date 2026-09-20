# 01 — Product Requirements (PRD)

## 1. Summary
A curated wallpaper app for Android featuring exclusively Khaled's own designs (graphic design / illustration / abstract). Users browse collections, preview, set wallpapers, favorite, and unlock premium wallpapers via subscription or reward ads. Built on the WallApp (Panels) codebase.

## 2. Problem / opportunity
- Wallpaper apps are either huge low-quality aggregators or single-artist apps with weak UX.
- Panels proved a single curator + quality art + clean UX + subscription can work. The code is now open-source; the differentiator is the art and the brand, not the engineering.

## 3. Target users
- **Primary:** Android users (18–35) who care about how their phone looks; follow design/tech creators.
- **Secondary:** fans of the artist's work coming from Instagram/Behance/Dribbble.

## 4. Goals (v1)
1. Ship a polished Android app on Google Play with ≥ 60 original wallpapers across ≥ 5 collections.
2. Free-to-download experience that converts to subscription.
3. A content pipeline where adding a new collection is a 15-minute task (design → export → upload), no code change.

## 5. Non-goals (v1)
- iOS, desktop, web.
- Multiple artists / artist submissions / payouts.
- User uploads, comments, social features.
- Live wallpapers, widgets, themes/icon packs.
- Localization beyond EN (FR later; the codebase has a `translations_enabled` flag).

## 6. Business model (inherited from Panels)
| Tier | Access |
|---|---|
| Free (anonymous or signed-in) | All wallpapers flagged `free`; SD download of others via reward ad (N ads per wallpaper, configurable); native ads in feed |
| Subscriber | All wallpapers in HD, no ads, no reward-ad gate |
| Per-collection purchase (optional, supported by data model `purchasableProductIds`) | One collection in HD, forever |

Pricing TBD. Codebase supports monthly + annual subscriptions via RevenueCat; toggles: `upgrade_enable_any_subscriptions`, `upgrade_enable_annual_subscription`.

## 7. Functional requirements

### 7.1 Content browsing
- **Home feed**: highlights (wallpaper of the week, collection of the week, just added, most popular — each toggled by Remote Config), then folders/collections.
- **Explore**: browse by folder → collection → wallpaper.
- **Collection page**: grid of wallpapers, buy/subscribe CTA if premium.
- **Artist page**: bio, social links, collections. Single artist in v1 but keep the structure (cheap, and allows guest collabs later).
- **Search**: by wallpaper/collection/artist slugs (offline index shipped in `content-metadata`).
- **Singles**: standalone wallpapers not in a collection (`isSingle`, toggled by `content_show_singles`).

### 7.2 Wallpaper detail
- Full-screen preview with blurhash placeholder, light/dark-aware UI (`isDark`, `topColorShade`).
- Actions: **Set wallpaper** (home / lock / both), **Download** (SD free / HD premium), **Favorite**, **Share** (deep link).
- Premium gate → paywall or reward-ad flow.

### 7.3 Account
- Anonymous sign-in by default (required to read Storage). Google sign-in to sync favorites/purchases across devices. No email/password.
- Favorites, "current wallpaper", purchase records, notification opt-in synced to Firestore `users/{uid}`.
- Account deletion (Play policy requirement) — flag `accountDeleted` exists; verify the flow works end to end.

### 7.4 Monetization
- Paywall screen (remote-configurable copy/layout — `remotepaywall` modules).
- RevenueCat entitlements: `premium` (subscription) + optional per-collection entitlements.
- AdMob: native ads in feed (`feed_ads_enabled`), rewarded ads to unlock (`reward_ads_*`), GDPR consent via Google UMP (`privacymessaging-google`).

### 7.5 Settings
Theme (system/light/dark), notifications, restore purchases, manage subscription, licenses, privacy policy, terms, delete account, app version.

### 7.6 Onboarding
First-run screen (value prop + sign-in optional) → home onboarding tooltips.

## 8. Non-functional
- Cold start < 2 s on a mid-range device (baseline profiles module exists).
- Images: WebP, sized variants per UI slot (see 05-backend-schema). Target < 150 KB for feed thumbs.
- Offline: last-fetched content + favorites available offline; downloads cached.
- minSdk: keep upstream value unless a reason to change (check `app/android/android.gradle.kts`).
- Crash-free sessions ≥ 99.5 % (Crashlytics).
- Play policies: data safety form, account deletion, ads/UMP consent, subscription disclosures.

## 9. Success metrics (first 90 days)
- Installs, D1/D7 retention, wallpaper set-rate per session, free→paid conversion, ARPU, reward-ad completion rate, crash-free rate.

## 10. Open questions
- App name, brand, tagline.
- Price points and whether to offer lifetime.
- Content cadence (weekly drop? monthly collection?).
- Reward-ad rule: how many ads to unlock one SD wallpaper.
