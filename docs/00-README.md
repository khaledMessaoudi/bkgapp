# bkgapp — Project docs

Working name: **bkgapp** (rename when brand is decided). Fork of [panels-art/WallApp](https://github.com/panels-art/WallApp) (Apache-2.0), the open-sourced Panels codebase.
Fork: https://github.com/khaledMessaoudi/bkgapp

| Doc | What it is |
|---|---|
| 01-PRD.md | Product: what we build, for whom, v1 scope, success metrics |
| 02-TRD.md | Tech: stack inherited from WallApp, what we keep / change / remove |
| 03-app-flow.md | Screens and navigation (Mermaid) |
| 04-ux-design-brief.md | Design brief + how the theme is wired in Compose |
| 05-backend-schema.md | Firestore, Storage layout, content JSON, Remote Config, RevenueCat, AdMob |
| 06-implementation-plan.md | Phased plan with concrete tasks and verification commands |
| tasks/phase-N-*.md | One task brief per phase, handed to Claude Code ("Read docs/tasks/<file> and do it") |
| CHANGES.md | Every deviation from upstream (created in Phase 2) |
| ../CLAUDE.md | Context file for Claude Code (put at repo root) |

## Decisions log
| Date | Decision |
|---|---|
| 2026-09-20 | Same concept as Panels: curated wallpapers, own designs only |
| 2026-09-20 | Android only for v1 (iOS/desktop code kept in repo but not built) |
| 2026-09-20 | Keep Panels business model: free tier + subscription (RevenueCat) + reward ads + feed ads (AdMob) |
| 2026-09-20 | Solo dev (Khaled + Claude Code, JetBrains plugin in Android Studio) |
| 2026-09-20 | Phase 0–1 done: Android Studio + Claude Code installed, untouched fork builds and runs in emulator |
| 2026-09-20 | Workflow: planning/design decisions in the claude.ai Project, code work in Claude Code; the repo (docs/ + CLAUDE.md) is the single source of truth between the two |
| — | App name / brand: **TBD** |
| — | Android package name: **TBD** (e.g. `com.kuix.bkgapp`) |
| — | Pricing (monthly / annual / lifetime?): **TBD** |
| — | Image hosting: Firebase Storage for v1, revisit (Cloudflare R2/Images, Bunny) if slow |

## Legal notes (from upstream)
- Apache-2.0: fine to use commercially; keep the LICENSE file and copyright notice.
- "Panels", "MKBHD", the Panels logo and any Panels art are **not** licensed. Strip every mention before release (strings, screenshots, icons, store listing).
- Demo wallpapers in `demo-assets/` are programmatically generated placeholders — replace with your own.
