# 05 — Backend schema

Everything below is what the upstream code expects today. Change only with a matching code change.

## 1. Firebase project
Services: Authentication (Anonymous, Google), Firestore, Storage, Remote Config, Crashlytics, Cloud Messaging (optional v1).
Environments: `dev` and `prod` Firebase projects (upstream has `Info-Dev`/`Info-Prod` on iOS and `RemoteApiExportSpecStaging`; mirror with two `google-services.json` via Android build types or flavors).

## 2. Firestore — `users/{uid}`
Rules (`firebase-backend/firestore.rules`): a user can read/create/update only their own doc; no deletes; `userId`/`epochCreated` immutable; anonymous → non-anonymous allowed, reverse not.

| Field | Type | Req | Notes |
|---|---|---|---|
| userId | string | ✓ | == uid |
| epochCreated | number | ✓ | ms |
| epochLastUpdated | number | ✓ | |
| epochLastSeen | number | ✓ | |
| isAnonymous | bool | ✓ | |
| loginType | string | ✓ | `firebase/anonymous` · `firebase/google` · `firebase/apple` · `none` |
| email | string | | regex-validated |
| favoriteIds | list<string> | | wallpaper ids |
| currentWallpaperIds | map | | e.g. home/lock → id |
| dataVersion | number | | migration marker |
| followingIds | list | | artists followed (hide in v1 or keep) |
| purchaseRecords | list | | RevenueCat mirror |
| deviceInfo | string | | |
| currency | string | | |
| newsletter | bool | | |
| flags | list | | |
| wallpaperDownloadEvents / wallpaperDownloadEvents2 | list | | analytics-ish; consider trimming |
| receiveNotifications | bool | | |
| accountDeleted | bool | | set on delete; data still exists → add a scheduled Cloud Function to purge if required by policy |

No other collections. Content is **not** in Firestore.

## 3. Storage — content API
Rules: `allow read: if request.auth != null` (anonymous counts). Writes only via admin SDK (export tool).

Layout (from `demo-assets/api/99999999/`, a real export):
```
api/<version>/spec.json
api/<version>/content-<c>              # NetworkContent JSON (all wallpapers, categories, artists, folders)
api/<version>/content-metadata-<c>     # search index
api/<version>/media-<c>-<kind>-<bucket>/<mediaId>...
```
`spec.json` example:
```json
{"content":"api/99999999/content-1a",
 "search":"api/99999999/content-metadata-1a",
 "media":{"root":"api/99999999/media-1a","p":["i","c"],
          "b":["p~s","p~five0","p~a~n","p~a~xl","p~uhd","f~fo","t~s","t~m","t~l"]}}
```
- `p` (kind): `i` = image, `c` = **TBD** (likely "color"/blurhash or compressed variant — confirm in `mediamap`/`FirebaseStorageDownloader`).
- `b` (buckets): `p~s / p~five0 / p~a~n / p~a~xl / p~uhd` = portrait sizes; `f~fo` = full/original; `t~s/m/l` = thumbnails. Exact pixel sizes: read `shared/data/mediamap*` + `NetworkImageSubLayoutParams` and record them here.
- How the app finds the current `spec.json` path: `shared/data/remoteendpoint` (+ RC key `image_host_name` for an alternate host). Document the endpoint URL once identified.

## 4. Content JSON models (`shared/data/content-network/.../model`)
```kotlin
NetworkContent(wallpapers, categories, artists, folders)

NetworkWallpaper(id, label, collectionLabel, type, artistId,
  dlm: {w, h, hd: mediaId, sd: mediaId},   // download media
  isDark, topColorShade?, categoryId, isSingle,
  previews: {standard: [NetworkMedia]}, slugs: [String],
  aie: Boolean = false /*AI-enhanced*/, free: Boolean = false)

NetworkCategory(id, label, artistId, featureBannerImage?, categoryType,
  previewRemixId, remixIds: [String], slugs, purchasableProductIds?)
  // "category" == a collection; "remix" == a wallpaper id

NetworkArtist(id, label, profileImage, featureBannerImage?, slugs, categoryIds, socialLinks)

NetworkFolder(id, title, titleTwoLines, remixIds, collectionIds?, profileImage, featureBannerImage)
  // folder == a home-feed section grouping collections/wallpapers

NetworkMedia(type?: String /*null = image*/, id: Long, w?, h?, blurHash?)

NetworkPurchasableProductIds(appStore, playStore, revenueCat)
```
v1 data: 1 artist (Khaled), N categories (collections), M folders (e.g. "Featured", "Abstract", "Minimal"), wallpapers with `free` set on ~20 %.

## 5. Remote Config keys (from `remoteconfig-api`) — launch defaults
| Key | Launch value |
|---|---|
| account_backend_enabled | true |
| account_sign_in_google_enabled | true |
| account_sign_in_apple_enabled | false |
| content_show_singles | true/false (decide) |
| feed_ads_enabled | true |
| highlight_artist / highlight_collection_of_the_week / highlight_just_added / highlight_most_popular / highlight_wallpaper_of_the_week | ids / true — set after content exists |
| image_host_name | "" (Storage) |
| reward_ads_enabled | true |
| reward_ads_max_count_to_unlock_single | 1–3 (decide) |
| reward_ads_enable_consecutive_plays | false |
| reward_ads_internal_probability | 0 |
| reward_ads_unlock_wallpaper_on_failure | true (be kind) |
| translations_enabled | false |
| upgrade_enable_any_subscriptions | true |
| upgrade_enable_annual_subscription | true |
| use_internal_ads_for_gdpr | false |

## 6. RevenueCat
- Project → Play Store app → API key (Android). Products in Play Console: `premium_monthly`, `premium_annual` (+ `premium_lifetime` optional), base plans + offers (intro/trial).
- Entitlements: `premium`. Offerings: `default` with the packages above.
- Per-collection: entitlement per collection id if used; product ids referenced in `NetworkCategory.purchasableProductIds`.
- Webhooks not needed for v1 (app writes `purchaseRecords`).

## 7. AdMob
- App id in `AndroidManifest`, units: native (feed), rewarded (unlock). Test ids in debug (`di-buildconfig-debug`).
- UMP consent form configured in AdMob → Privacy & messaging (GDPR + US states).

## 8. Deep links
`https://<domain>/w/<wallpaperId>` (and collection/artist) → `assetlinks.json` on the domain with the release signing SHA-256. Host TBD.

## 9. Privacy policy / terms
Static pages on the same domain; URLs referenced in Settings and Play listing.
