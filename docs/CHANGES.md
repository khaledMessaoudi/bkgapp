# CHANGES — deviations from upstream

Every change to code inherited from [panels-art/WallApp](https://github.com/panels-art/WallApp) goes here, newest first. Tag `v0.0.0-upstream` marks the untouched fork.

Format: `- <date> · <area> — <what and why> (<commit or file>)`

## Deviations
_None yet — Phase 2 is read-only investigation._

## Non-deviations (ours, not upstream code)
- 2026-09-20 · build — reduced Gradle/Kotlin daemon heap in `gradle.properties` to fit available RAM (`eda9372`). Local build config, not app behaviour.
- 2026-09-20 · docs — added `CLAUDE.md` and `docs/` (`ccd669b`).
- 2026-09-20 · tools — added `tools/content_build.py` + `tools/validate_content.py`, our own content pipeline. New files only; `service/service-export-remoteapi` is untouched and still does the encrypt+upload step.
