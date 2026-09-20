# tools — content pipeline

`content_build.py` turns master images plus a `content.yaml` catalogue into the content API
the app consumes. `validate_content.py` checks the result before it is uploaded.

Why Python and not the Kotlin export tool: `service/service-export-remoteapi` only encrypts an
already-built folder and uploads it — it generates no images, variants, blurhashes or JSON
(see `docs/05-backend-schema.md` §10). Everything missing is image work, so it lives here and
the upstream tool stays untouched.

## Setup
```
pip install -r tools/requirements.txt
```

## Build
```
python tools/content_build.py content/content.yaml -o build/content --clean
python tools/validate_content.py build/content/api/<version>
```
Output:
```
build/content/api/<version>/     # flat: key1, spec.json, content-1a, content-metadata-1a, media-1a-c-<bucket> ×9
build/content/media/<mediaId>/<w>x<h>.webp
```
Then:
1. upload `build/content/media/` to the public media bucket at `media_base_url`,
2. copy `build/content/api/<version>/` to `../wallapp-api/api/v0/` and run `service-export-remoteapi`,
   which encrypts each file to `<name>.data` and uploads it to Firebase Storage.

Start from `content.example.yaml`. Options worth knowing:
- `buckets: ["p~a~n"]` — build one device bucket instead of nine while iterating. Nine buckets
  take roughly ten seconds per master.
- `--dry-run` validates the catalogue and reports counts without writing anything.
- `--clean` wipes the output folder. Without it, existing variant files are reused as-is, which
  is fast but will keep stale images if a master changed — use `--clean` for a real export.

## Local loop: run your content on the Android emulator
No Firebase project needed. Four pieces: the Storage emulator serves the encrypted API, a plain
HTTP server serves the images, and the app is pointed at both.

```
# 1. emulators (needs java on PATH; firebase-tools installed globally)
cd firebase-backend && firebase emulators:start --only auth,storage

# 2. build, check, encrypt, upload — version must be v0 to match RemoteEndpointsSpecs.Staging
python tools/content_build.py content/content.yaml -o build/content --clean
python tools/validate_content.py build/content/api/v0
python tools/encrypt_api.py build/content/api/v0
python tools/upload_emulator.py build/content/api/v0

# 3. serve the images where the device can reach them
cd build/content/media && python -m http.server 8787 --bind 127.0.0.1
```
Set `media_base_url: "http://10.0.2.2:8787"` in the catalogue — `10.0.2.2` is the host as seen
from the Android emulator, and the debug build already permits cleartext to it
(`shared/data/resources/src/androidDebug/res/xml/network_security_config.xml`).

Then flip `RUN_FIREBASE_ON_LOCAL_EMULATORS = true` in
`shared/data/account-api/.../AccountManager.kt` (upstream's own emulator switch; keep it out of
commits), build and install:
```
./gradlew :app:android:assembleDebug
adb install -r app/android/build/outputs/apk/wallApp/debug/android-wallApp-debug.apk
adb shell pm clear com.example.wallapp     # content is cached; clear between exports
adb shell am start -n com.example.wallapp/wallapp.activity.MainActivity
```
`pm clear` matters — without it the app keeps serving the previous export from its cache.
Firestore is not emulated here, so the user profile sync logs a non-fatal error; harmless.

## What it generates
- **Variants**: per device bucket (`docs/05-backend-schema.md` §3.5), one WebP per UI slot, centre
  cover-cropped, never upscaled. Slots that resolve to the same pixel size share one file, so the
  nine buckets produce about four distinct sizes per master.
- **Blurhash**: 4×4 components, matching the upstream export. Implemented here (no extra
  dependency) and verified by decoding back to within ~5/255 of the original.
- **Ids**: derived from stable hashes of the catalogue path, so they survive rebuilds. Changing a
  wallpaper's collection or filename changes its id and invalidates client caches.
- **Search index**: `content-metadata-<c>` from the `tags` / `colors` / `search_terms` in the YAML.

## Rules the app enforces at runtime
Content can deserialize cleanly and still crash or hang the app. `validate_content.py` fails the
build on each of these; `docs/05-backend-schema.md` §4b has the code references.
- a `Collection` needs `product_ids`; ungated wallpapers go in a `Singles` collection whose id is `singles`
- a folder with id `f~justadded` must exist and hold wallpapers, or first run never leaves a blank screen
- the search index needs `artistMetadata` and `folderMetadata`, and every entry needs
  `titleSuggestions` and `description` — one missing key stalls loading with no visible error

## Known gaps
- Media ids are emitted as **numbers** in the content JSON. The demo export quotes them, but the
  app's `Json` is not lenient, so a quoted `Long` would fail to decode.
- `dlm.hd` and `dlm.sd` point at the same media id; the two differ only by the slot they are
  fetched under (`dhd` full size, `dsd` half).
- `encrypt_api.py` reimplements the Kotlin exporter's AES-256-GCM in Python. The layout is pinned
  by `shared/core/security/src/commonTest/.../PythonCiphertextCompatTest.kt` and confirmed on
  device; `service-export-remoteapi` remains the path for uploading to real Firebase Storage.
