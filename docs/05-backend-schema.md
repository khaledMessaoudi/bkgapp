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

### 3.1 How the app finds `spec.json`
| Step | Code | Value today |
|---|---|---|
| Bucket | Firebase Storage default bucket, from `app/android/google-services.json` | `panels-oss.firebasestorage.app` (project `panels-oss`) — replace in Phase 3 |
| Path prefix | `RemoteEndpointsSpecs.Staging` (`shared/data/remoteapi/.../RemoteEndpointsSpecs.kt:9-13`), bound by `RemoteEndpointsSpecRepositoryDefault` | `base = "api/v0/"` |
| Filename | `RemoteEndpointTrack.specFilename` (`remoteendpoint/.../RemoteEndpointTrack.kt:13-18`) | Production `spec.json` · Staging `spec-s.json` · Development `spec-d.json` |
| Track | `RemoteApiEndpointRepositoryConfig.remoteEndpointTrack` | `Production` |
| Download | `FirebaseStorageDownloader.downloadFile(path, appendData = true)` appends `.data` (`download/.../FirebaseStorageDownloader.android.kt:35`) | fetches **`api/v0/spec.json.data`** |

So the full path is `<default Storage bucket>/api/v0/spec.json.data`. Only `RemoteEndpointsSpecs.Staging` exists — `RemoteEndpointsSpecRepositoryDefault` and `…Staging` both return it. To move the API, change `google-services.json` (bucket) and/or `RemoteEndpointsSpecs` (`apiVersion` / `base`). There is **no build-config or Remote Config override for the API host**: `di-buildconfig-{debug,release}` only wire billing and debug managers.

`image_host_name` (RC, default `"example"`) is **dead code** in the OSS build — `RemoteConfigData.imageHostName` has no consumer. Upstream used it to swap the imgix host (`ImageHostUrlMapperConfigImgix.hostPrefixes = ["https://<appname>.imgix.net"]`). It does **not** affect `spec.json` resolution.

### 3.2 Encryption — every API file is encrypted
`RemoteApiDefault.getFile()` decrypts everything it downloads (`remoteapi/.../RemoteApiDefault.kt:32-56`).
- Algorithm: **AES-256-GCM** (`security/.../EncryptionManager.android.kt`); key = UTF-8 bytes of the key string, `copyOf(32)` (zero-padded or truncated to 32 bytes).
- The 12-byte constant `[47,-93,98,49,107,77,-74,68,-17,-105,89,86]` is passed as **associated data (AAD)**, *not* as the GCM nonce. The nonce is generated per file by the crypto library — which is why `RemoteApiStorageManager` notes "the results are NOT deterministic".
- Key file: `api/v0/key1`, uploaded **unencrypted**, fetched with `appendData = false`, content used verbatim (`RemoteApiSecretManagerDefault.getDeobfuscatedKey` is identity in the OSS build; Panels' obfuscation was not open-sourced). Offline/preset key: `bd446249-1c66-4a67-b49b-c605f922b5cb`.
- Every other file is uploaded as `<name>.data` and requested with `.data` appended.

> Assumption to verify before writing a Python encryptor: cryptography-kotlin's `AES.GCM` ciphertext layout is `nonce(12) || ciphertext || tag(16)`. Round-trip one file through `service-export-remoteapi` and decrypt it in Python to confirm.

### 3.3 Layout
```
api/<version>/key1                            # plaintext AES key
api/<version>/spec.json.data                  # RemoteEndpoints
api/<version>/content-<c>.data                # NetworkContent (wallpapers, categories, artists, folders)
api/<version>/content-metadata-<c>.data       # search index
api/<version>/media-<c>-<p>-<b>.data          # one media map per platform × bucket (18 files)
```
`RemoteApiStorageManager.uploadFileToStorage` uploads to `base + file.name` — **subdirectories are flattened**, so the export input folder must be flat.

`spec.json` (demo export):
```json
{"content":"api/99999999/content-1a",
 "search":"api/99999999/content-metadata-1a",
 "media":{"root":"api/99999999/media-1a","p":["i","c"],
          "b":["p~s","p~five0","p~a~n","p~a~xl","p~uhd","f~fo","t~s","t~m","t~l"]}}
```
`root` must contain `-` (asserted in `RemoteEndpointMediaMap.init`). Media map endpoint = `"$root-$p-$b"`.

### 3.4 `p` — platform key, not "color"
`ImageHostPlatform` (`core/common/.../image/host/ImageHostPlatform.kt`): `i` = **Apple/iOS**, `c` = **Compose** (Android + desktop). Android always resolves to `c` (`getSystemImageHostPlatform()` → `Compose`). Both sets exist so one export serves both apps. Android-only v1 can trim `spec.json`'s `p` to `["c"]` — `getEndpoint()` only `require`s that the requested key is in the list — which halves the media map files from 18 to 9.

### 3.5 `b` — device buckets (not UI slots)
A bucket is a **device class**, picked once per device by `ImageBucketArbitrator` (first bucket whose max width, height and density all fit; else the largest). From `ImageBucketSpecs.kt`:

| Key | Label | max W×H px | max density |
|---|---|---|---|
| `p~s` | Phone Smallest | 960 × 1800 | 2.0 |
| `p~five0` | Phone 5.0 inch | 1080 × 2160 | 2.75 |
| `p~a~n` | Phone Apple Normal | 1179 × 2556 | 3.0 |
| `p~a~xl` | Phone Apple XL | 1290 × 2796 | 3.0 |
| `p~uhd` | Phone UHD | 1440 × 3120 | 4.0 |
| `f~fo` | Foldable Flip Open | 2208 × 1840 | 4.0 |
| `t~s` | Tablet Small | 744 × 1133 | 1.0 |
| `t~m` | Tablet Medium | 1848 × 2960 | 3.0 |
| `t~l` | Tablet Large | 2048 × 2732 | 3.0 |

The list is sorted ascending, so a 1080×2400 @3.0 phone lands in `p~a~n`. `ImageBucketSpecs.Preset = PhoneAppleNormal`.

### 3.6 UI slots — the media map keys
A media map file is `{"version": 1, "data": {<mediaId>: {<slotKey>: <absolute URL>}}}` (`NetworkMediaData`; `NetworkMediaMap = Map<String, String>`). Slot keys come from `SizedImage` (`data/base/.../image/sized/SizedImage.kt`):

| Key | Slot | Used for |
|---|---|---|
| `dhd` | DownloadableWallpaperHd | the file the user downloads / sets (full res) |
| `dsd` | DownloadableWallpaperSd | SD download variant |
| `fs` | FullScreen | full-screen preview |
| `s` | Showcase | wallpaper detail showcase |
| `e` | Exhibit | Explore carousel top items (square, `applyCrop = false`) |
| `wfs` | WallpaperFeedSingle | feed item, single span |
| `wft` | WallpaperFeedTrack | feed item, square |
| `wcs0` / `wcs1` / `wcs2` | WallpaperCollectionSmallLayer0-2 | small collection card, 3 stacked layers |
| `wcl0` / `wcl1` / `wcl2` | WallpaperCollectionLargeLayer0-2 | large collection card, 3 stacked layers |
| `as` / `am` | ArtistSmall / ArtistMedium | artist profile image (56.dp / medium) |

Which media id carries which slots (demo export, 412 entries):
- wallpaper `dlm.hd` / `dlm.sd` ids → `dhd`, `dsd`
- wallpaper `previews.standard[].id` → `s`, `wfs` (plus `wft`, `wcs0-2`, `wcl0-2` when the wallpaper is a collection preview)
- artist / folder `profileImage` and `featureBannerImage` ids → `as`, `am`, `e`

**No fixed pixel size per slot exists in the data.** `ImageSizeMapperDefault` + `ImageViewSpecFactoryDefault` compute the wanted size at runtime from the live window size in dp, and `ImageHostUrlMapperImgix` appends imgix params (`w`, `h`, `fit`, `fm`) **only when the URL starts with a configured imgix host**. Panels served every slot from one imgix original; the bucket only capped the resolution. We have no imgix, so our tool bakes one file per (slot × bucket) and the sizes in §10 are our choice, not something the app dictates.

### 3.7 Media binaries
The images themselves are **not** in the API folder and **not** encrypted — the media map holds absolute URLs. The demo points at `https://storage.googleapis.com/wallapp-assets/content/<artist>_<hash>/<file>.png`, a separate public bucket. We can host ours in our own Storage bucket (public read) or any CDN.

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
Verified against `demo-assets/api/99999999/content-1a` (200 wallpapers, 19 categories, 10 artists, 1 folder):
- `dlm` is `{"hd": <int>, "sd": <int>, "w": <int>, "h": <int>}` — media ids as **numbers**, and `w`/`h` are the master's dimensions.
- `NetworkMedia.id` is a `Long` in Kotlin but appears **quoted** in the JSON (`"id": "962966773"`), and media map keys are strings too. Serialization accepts both; emit whichever, stay consistent.
- `type` on a wallpaper is a string (`"parallax"` in the demo); `collectionLabel` is the collection's label repeated on the wallpaper (`"Singles"`).
- `folders[].featureBannerImage` carries `w`/`h`; `profileImage` usually doesn't.

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

## 10. Content build tool — requirements
What `tools/content_build.py` (Pillow + blurhash) has to produce so the app can consume it, derived from §3 and §4. The upstream generator was never open-sourced, and `service/service-export-remoteapi` **only encrypts and uploads an already-built folder** — it generates no images, no variants, no blurhashes, no JSON.

### 10.1 Input
```
content/
  content.yaml                     # the whole catalogue: artists, collections, folders, wallpapers
  wallpapers/<collection>/<name>.png   # one master per wallpaper, portrait, largest size we have
  artists/<artistId>/profile.png
  folders/<folderId>/banner.png
```
`content.yaml` carries everything the images can't: labels, ids (or let the tool derive them), `free`, `isDark`, `categoryType`, `remixIds` order, folder composition, social links, search tags. Masters should be ≥ 1440 × 3120 (the largest phone bucket) so no bucket upscales; foldable/tablet buckets are wider than any portrait master, so the tool must letterbox or crop — decide per bucket, don't upscale.

### 10.2 Output — the flat API folder
```
api/<version>/
  key1
  spec.json
  content-<c>
  content-metadata-<c>
  media-<c>-c-<bucket>        # 9 files, one per bucket (Android only)
  media-<c>-i-<bucket>        # only if iOS is ever built
```
`<c>` is any cache-busting suffix (demo uses `1a`); bump it whenever the content changes so clients re-fetch. Flat — `RemoteApiStorageManager` flattens subdirectories on upload. Feed this folder to the export tool as `ApiExportDataRoot/<apiVersion>`, which encrypts every file to `.data` and uploads.

### 10.3 Media files and the media map
For each (media id × bucket) the tool writes an image file to the public media bucket and an entry in the 9 bucket media maps. Sizes as a fraction of the bucket's `maxWidthPx` × `maxHeightPx` (see §3.5) — our choice, since nothing in the app pins them:

| Slot | Size | Notes |
|---|---|---|
| `dhd` | bucket max W × H | the download / set-wallpaper file |
| `dsd` | 50 % of bucket max | SD download |
| `fs`, `s` | bucket max W × H | full-screen and showcase previews |
| `wfs` | 50 % width, same aspect | feed item, single span |
| `wft` | 50 % width, square crop | feed item, square |
| `e` | full width, square crop | Explore carousel (no crop applied by the app) |
| `wcs0-2` | 50 % width, card aspect | small collection card layers |
| `wcl0-2` | full width, card aspect | large collection card layers |
| `as` | 168 px square | 56 dp @3x |
| `am` | 288 px square | 96 dp @3x |

Dedupe: identical slot sizes within a bucket should point at the same file. Format: **WebP** everywhere (q≈85 previews, q≈90 downloads) — see §10.6.

Media map file, per bucket:
```json
{"version": 1, "data": {"962966773": {"s": "https://…/w/<id>/s.webp", "wfs": "https://…"}}}
```

### 10.4 Ids and naming
| Thing | Shape in the demo export | Rule for us |
|---|---|---|
| artist id | `a~dark` | `a~<slug>` |
| wallpaper id | `a~dark_1a6be1c2` | `<artistId>_<8 hex of a stable hash>` |
| collection id | `dark~singles` | `<artistSlug>~<collectionSlug>` |
| folder id | `f~justadded` | `f~<slug>` |
| media id | `962966773` | stable positive integer (hash of the source file path), **unique across all media** |
| slug | `w/2QR`, `dark/singles` | wallpapers `w/<short>`, collections `<artist>/<collection>` |

Media ids are numbers in `dlm` (`{"hd": 2077508249, "sd": …, "w": 950, "h": 720}`) but **strings** in `previews.standard[].id`, `profileImage.id` and `featureBannerImage.id`, and strings as media map keys. `NetworkMedia.id` is a `Long`, so keep them plain integers with no leading zeros. Ids must be stable across rebuilds or every client cache is invalidated.

### 10.5 Blurhash
Every `NetworkMedia` (`previews.standard[]`, `profileImage`, `featureBannerImage`) carries a `blurHash` string. The app only **decodes** (`core/common/.../image/hash/blur/`), so the tool must generate them — standard blurhash, 4×3 components as in the demo (`U03baYj[Ioj[j[fQfQfQD%fQt7fQj[fQfQfQ`), computed from a small downscale of the master. `dlm` media (download files) have no blurhash.

### 10.6 Formats
Nothing in the load or export path requires PNG. The demo is PNG only because its media were PNG; the export tool treats files as opaque bytes and doesn't touch media at all. On Android, Coil 2.7 decodes WebP natively, `ImageHostPlatformConfigAndroid.inAppComposeImageHostFormat = WebP` and `galleryImageHostFormat = WebP` (AVIF is disabled upstream: "too unreliable", issue #851). `Bitmap.CompressFormat.PNG` appears only in `BitmapUtils.saveBitmap`, which is the local disk cache, not the network format. **Ship WebP.**

### 10.7 Search index — `content-metadata-<c>`
```json
{"remixMetadata": [{"remixId": "a~dark_1a6be1c2", "artistNames": ["dark"],
  "title": "radial 02", "collectionTitle": null,
  "styles": [{"t": "amoled", "r": 1.0}], "tags": [{"t": "dark", "r": 1.0}],
  "colors": [{"t": "dark", "r": 0.667}], "searchTerms": [{"t": "black wallpaper", "r": 1.0}]}]}
```
`t` = term, `r` = relevance 0-1. Titles are lowercased. Tags/terms come from `content.yaml`; `colors` can be derived from the master with Pillow.

### 10.8 Build steps
1. Read `content.yaml`, resolve ids, validate every referenced id exists (`previewRemixId`, `remixIds`, `collectionIds`, `categoryId`, `artistId`).
2. Per master: compute media ids, generate the slot × bucket variants, blurhashes, and `w`/`h`.
3. Emit `content-<c>`, `content-metadata-<c>`, the 9 media maps, and `spec.json` (with `p: ["c"]` for Android-only).
4. Upload media binaries to the public media bucket.
5. Hand `api/<version>/` to `service-export-remoteapi` (encrypt + upload), or re-implement AES-256-GCM in Python once §3.2's ciphertext layout is confirmed.

### 10.9 Local prototyping without Firebase
There is **no bundled-content path in the code**. `demo-assets/api/99999999/` is documented as "not actually used… included for reference"; the FAQ's claim that the release "is configured to support the use of API data bundled with the application" is not backed by anything in this tree — nothing loads that folder, and `FactoryCommon` binds `RemoteApiEndpointRepositoryDefault` (network) and `RemoteEndpointsSpecRepositoryDefault` (`api/v0/`). Today the demo app pulls from the `panels-oss` Firebase Storage bucket.

Three options to try our own content, cheapest first:
1. **Our own Firebase dev project** (Phase 3 anyway) — upload `api/v0/` there, no code change beyond `google-services.json`.
2. **Firebase Storage emulator** — `firebase-backend/` already has an emulator setup (`doc/firebase-emulator.md`); point the app at it in debug.
3. **Preset repositories** — `RemoteApiEndpointRepositoryPreset` + `RemoteEndpointsRepositoryPresetDefault(jsonString)` already exist and take a `spec.json` string; binding them in the debug DI factory with a bundled string and local/`file://` media URLs would give a fully offline build. Note media maps and content would still be fetched through `RemoteApi`, so this only replaces endpoint resolution — a full offline mode needs a `RemoteApi` that reads from app assets. Log it in `CHANGES.md` if we do it.
