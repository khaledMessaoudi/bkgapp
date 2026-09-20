# 04 — UX / UI design brief

Owner: Khaled (design). This brief exists so the design decisions are made *before* touching Compose, and so Claude Code knows which tokens/components to edit.

## 1. Product feel
- Positioning: a gallery, not a store. Art first, chrome second.
- The app UI should be near-invisible: neutral surfaces, wallpapers provide the color. Dynamic accents from the wallpaper are already supported (`DynamicTheme.kt`, `isDark`, `topColorShade`).
- Reference the Panels screenshots in `doc/img/` for the inherited layout; we redesign surfaces, type, iconography, motion — not the information architecture (that's expensive).

## 2. Deliverables (Figma)
1. **Brand**: name, wordmark, app icon (adaptive icon: foreground + background layers, 108dp grid), splash.
2. **Foundations**: color tokens (light/dark), type scale, spacing (4dp grid), radii, elevation/blur rules, icon set (Material Symbols vs custom — upstream mixes both).
3. **Screens** (phone, 1080×2400 frame): FirstRun, Home, Explore, Folder, Collection, Wallpaper detail (+ action sheet: set/download/share), Favorites, Search (input + results + empty), Artist, Paywall, Reward-ad unlock dialog, Profile, Settings, Onboarding tooltips, Loading/Error/NoData, Delete-account confirm.
4. **Components**: wallpaper card (portrait, 3 sizes: feed thumb / grid / hero), collection card, folder header/banner, premium badge, free badge, bottom bar, sheet, button set, ad slot (native ad must look like an ad — Google policy), toast.
5. **States** per screen: loading (blurhash placeholders), empty, error, offline, premium vs free, subscribed vs not.
6. **Motion**: shared-element from card → detail, pager swipe, set-wallpaper success (replace the upstream confetti Lottie or keep), paywall entrance.
7. **Store assets**: Play listing screenshots (6–8), feature graphic 1024×500, icon 512.

## 3. Constraints coming from the codebase
- Theme lives in `shared/presentation/theme-ui`: `AppTheme.kt` (colors/type), `AppShapes.kt` / `AppCornerBasedShape.kt` (radii), `DynamicTheme.kt`, `SystemUiTheme.android.kt` (status/nav bar). Define Figma tokens with the same names to make the handoff mechanical.
- Fonts: check `ShowcaseTypefaceScreen` for how typefaces are registered; add ours as Compose resources.
- Layout params for image slots exist (`NetworkImageSubLayoutParams`, `mediamap`); each UI slot maps to a media variant bucket (see 05). If you change card aspect ratios, the export buckets must change too.
- Native ads render via the Android SDK, not Compose — style within AdMob's native ad constraints.
- Paywall copy/layout is remote-configurable; design the paywall as a template with slots.

## 4. Accessibility / quality bar
- Contrast AA on all text over wallpapers (use scrims, not hope).
- Touch targets ≥ 48dp. Dynamic type up to 1.3×.
- Dark mode is the default look; light mode must still be complete.
- RTL not required for v1.

## 5. Questions to settle in the brief
- Name + wordmark direction.
- One accent color or fully dynamic from artwork?
- Card ratio: 9:19.5 (phone-true) vs 3:4 (denser grid)?
- Do free wallpapers show a watermark/badge or nothing?
