# Zap Launcher

A lightweight, fast, ad-free custom launcher for Android TV — built to run
smoothly on low-memory TVs like the TCL UnionTV (Android TV 9).

No ads. No tracking. No background services. No recommendations feed.
Just your apps, on a clean home screen.

## Features (v0.1)

- **Clean home grid** — full-screen wallpaper, apps in a centered grid
  (Wolf Launcher style), favorites pinned at the top.
- **Favorites first** — Nuvio TV, SmartTube and Moonlight are pinned at the
  top when installed; every other Leanback/sideloaded app is listed below.
- **Live clock & date** at the top right, settings gear next to it.
- **D-pad friendly** — smooth focus scale + highlight, made for the remote.
- **Settings** (everything else lives here, out of the way):
  - Wallpaper: 4 built-in gradients, or pick your own image from storage.
  - Hide apps from the home grid.
  - Reorder / add / remove favorite apps.
  - Shortcut to set Zap Launcher as the default home.
  - About (name + version).
- **Lightweight by design** — 3 dependencies only (AndroidX core-ktx,
  appcompat, leanback), zero permissions, lazy icon loading with a small
  in-memory cache.

## Install (sideload via ADB)

```sh
adb install app/build/outputs/apk/release/app-release.apk
```

Then press Home and choose **Zap Launcher** as the default home
(or use *Settings → Make Zap Launcher the default home* inside the app).

> Keep your previous launcher installed as a fallback until Zap Launcher
> is confirmed working as the default home.

## Build

Requirements: JDK 17, Android SDK with `platforms;android-34` and
`build-tools;34.0.0`.

```sh
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. A release keystore is
optional: if `zap.keystore.*` properties are present in
`~/.gradle/gradle.properties`, the release APK is signed with it;
otherwise it is built unsigned.

## Project layout

```
app/src/main/java/com/zetzzzz/zaplauncher/
├── MainActivity.kt       # Home screen: grid, clock, wallpaper
├── SettingsActivity.kt   # Settings + wallpaper picker + About
├── HideAppsActivity.kt   # Hide apps from the home grid
├── FavoritesActivity.kt  # Reorder / add / remove favorites
├── AppsRepository.kt     # Loads Leanback + sideloaded apps
└── Prefs.kt              # Tiny SharedPreferences wrapper
```

## Roadmap

- **v0.2** — folders (+PIN), widgets (clock/weather/system info), parental
  controls, icon packs, video wallpapers, backup/restore, Home Button
  Redirect for TVs that block third-party launchers.
- See [CHANGELOG.md](CHANGELOG.md) for the full history.

## License

MIT — see [LICENSE](LICENSE).
