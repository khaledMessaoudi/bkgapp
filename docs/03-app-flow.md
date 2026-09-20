# 03 — App flow

Screen names below map 1:1 to `shared/presentation/wallapp-ui/.../*Screen.kt` so Claude Code can find them.

## Launch
```mermaid
flowchart TD
  L[LoadingScreen<br/>init Koin, Firebase, RC, anon sign-in] --> F{first run?}
  F -- yes --> FR[FirstRunScreen<br/>value prop · Continue / Sign in with Google]
  F -- no --> H
  FR --> H[HomeScreen]
  L -- content fetch fails --> E[ErrorScreen / NoDataScreen<br/>retry]
  H --> HO[HomeOnboardingScreen<br/>one-time tooltips]
```

## Main navigation (bottom bar)
```mermaid
flowchart LR
  H[HomeScreen<br/>highlights + folders] 
  X[ExploreScreen<br/>folders → collections]
  S[SearchInputScreen] --> SR[SearchResultsScreen]
  FAV[FavoritesScreen]
  P[ProfileScreen<br/>account · purchases · settings]
  H --- X --- S --- FAV --- P
```

## Content drill-down
```mermaid
flowchart TD
  H[Home] --> FO[FolderScreen]
  H --> C[CollectionScreen]
  X[Explore] --> CS[CollectionsScreen] --> C
  FO --> C
  C --> W[Wallpaper detail<br/>full-screen pager in WallAppScreen]
  H --> A[ArtistScreen] --> C
  AS[ArtistsScreen] --> A
  SR[SearchResults] --> W
  SR --> C
  FAV[Favorites] --> W
  W --> |Set| SET{home / lock / both}
  W --> |Download| DL
  W --> |Favorite| FAV
  W --> |Share| DEEPLINK[deep link → W]
```

## Premium gate
```mermaid
flowchart TD
  W[Wallpaper detail: HD download / set premium] --> Q{entitled?}
  Q -- subscriber or collection owned --> OK[Download HD]
  Q -- free wallpaper --> OK
  Q -- no --> G{reward ads enabled?}
  G -- yes --> R[RewardAdInternalScreen / AdMob rewarded<br/>N ads → unlock SD]
  G -- no --> PW
  R -- user taps Upgrade --> PW[Paywall<br/>remotepaywall-ui]
  PW --> RC[RevenueCat purchase]
  RC -- success --> OK
  RC -- cancel --> W
```

## Account
```mermaid
flowchart TD
  P[ProfileScreen] --> SI{signed in?}
  SI -- anonymous --> GS[Google sign-in → links anon uid]
  SI -- Google --> OUT[Sign out]
  P --> ST[SettingsScreen<br/>theme · notifications · restore purchases · manage sub · licenses · privacy · delete account]
  ST --> DEL[Delete account → accountDeleted=true, sign out, wipe local]
  P --> CN[ConnectionsScreen<br/>social links — repurpose or hide]
```

## Debug-only
`ShowcaseAdsScreen`, `ShowcaseTypefaceScreen`, `ShowcaseIosNativeFeedScreen`, `IndexScreen` — keep behind debug build config.
