# Task: Phase 2 — Understand the content pipeline

Context: read `CLAUDE.md`, then `docs/06-implementation-plan.md` (Phase 2) and `docs/05-backend-schema.md` §3–4.
Status: Phase 1 done — the untouched fork builds and runs in the emulator.

## Step 0 — Wrap up Phase 1
- Tag the known-good state: `git tag v0.0.0-upstream && git push origin v0.0.0-upstream`
- Verify `docs/` and `CLAUDE.md` are committed and pushed.

## Step 1 — Investigation (read only, no code changes)
Answer each question with file paths + line references. If something can't be determined from the code, say so — don't guess.

1. **spec.json resolution.** How does the app build the URL/path of `api/<version>/spec.json`? Look at `shared/data/remoteendpoint`, `shared/data/remoteapi`, Remote Config key `image_host_name`, and `shared/di/di-buildconfig-{debug,release}`. What is the current default host/bucket and where would we change it?

2. **Media buckets.** In `spec.json`, `"p": ["i","c"]` and `"b": ["p~s","p~five0","p~a~n","p~a~xl","p~uhd","f~fo","t~s","t~m","t~l"]`. For each bucket: which UI slot uses it and what pixel size / aspect ratio / format is expected? What is kind `c` vs `i`? Look at `shared/data/mediamap*`, `NetworkImageSubLayoutParams`, `shared/presentation/image-ui`, and the Firebase Storage downloader.

3. **Export tool input.** What does `service/service-export-remoteapi` expect as input (folder layout, file names, JSON files)? Does it generate the resized variants and blurhashes itself, or expect them pre-made? Read `Constant.kt`, `RemoteApiStorageManager.kt`, `RemoteApiExportSpec.kt`, `RemoteApiExport.main.kt`. Note that only a Staging spec is wired.

4. **Bundled demo data.** How does the OSS build load `demo-assets/api/99999999`? Which module/flag switches between bundled and remote content? Could we point it at a local folder containing our own files for prototyping, without touching Firebase yet?

5. **Formats.** Does anything in the media loading or export path assume PNG, or can we ship WebP?

## Step 2 — Write it down
- Update `docs/05-backend-schema.md`: replace every "TBD" in §3 with the findings (bucket table: bucket → UI slot → size → format).
- Add a new section "Content build tool — requirements" listing exactly what a `tools/content_build.py` script (Pillow + blurhash) would have to produce so the app can consume it: folder layout, JSON files, media variants, naming, ids.
- Add a `docs/CHANGES.md` file (empty list for now) — every deviation from upstream goes there from now on.

## Step 3 — Report
Summarise the findings in the terminal in ≤ 20 lines, then stop. Do not start writing the content tool yet — the design gets decided first.
