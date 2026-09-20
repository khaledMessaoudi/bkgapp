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

## What it generates
- **Variants**: per device bucket (`docs/05-backend-schema.md` §3.5), one WebP per UI slot, centre
  cover-cropped, never upscaled. Slots that resolve to the same pixel size share one file, so the
  nine buckets produce about four distinct sizes per master.
- **Blurhash**: 4×4 components, matching the upstream export. Implemented here (no extra
  dependency) and verified by decoding back to within ~5/255 of the original.
- **Ids**: derived from stable hashes of the catalogue path, so they survive rebuilds. Changing a
  wallpaper's collection or filename changes its id and invalidates client caches.
- **Search index**: `content-metadata-<c>` from the `tags` / `colors` / `search_terms` in the YAML.

## Known gaps
- Media ids are emitted as **numbers** in the content JSON. The demo export quotes them, but the
  app's `Json` is not lenient, so a quoted `Long` would fail to decode.
- `dlm.hd` and `dlm.sd` point at the same media id; the two differ only by the slot they are
  fetched under (`dhd` full size, `dsd` half).
- No AES-GCM encryption here — the Kotlin export tool does it. Doing it in Python needs the
  ciphertext layout confirmed first (`docs/05-backend-schema.md` §3.2).
