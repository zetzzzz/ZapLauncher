# Changelog

All notable changes to Zap Launcher are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-11-11

### Added
- Initial release: lightweight custom launcher for Android TV (minSdk 28).
- Home screen with full-screen wallpaper (default dusk gradient), a centered
  app grid (Wolf-style), live clock + date and a settings gear at the top right.
- App list from PackageManager (Leanback + plain launcher categories, so
  sideloaded apps are always shown); lazy icon loading with a small cache.
- Favorites pinned at the top when installed: Nuvio TV, SmartTube, Moonlight.
- D-pad navigation with focus scale + highlight ring.
- Settings: wallpaper picker (4 built-in gradients or a custom image from
  storage), hide apps, reorder/add/remove favorites, default-home shortcut,
  About dialog.
- No ads, no tracking, no background services, zero permissions.

## [Unreleased]

### Planned for 0.2.0
- Folders (+PIN protection), widgets (clock/weather/system info).
- Parental controls, icon packs, video wallpapers.
- Backup/restore of launcher settings.
- Home Button Redirect for TVs that block third-party launchers.
