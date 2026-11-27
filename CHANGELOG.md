# Changelog

All notable changes to Zap Launcher are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.9] - 2026-11-12

### Fixed
- Custom icon never appeared on the home grid: the v0.1.5 anti-flicker
  skip only compared the app order, and a changed icon leaves the order
  identical — so the grid was never rebound and the saved icon never
  showed. The skip now also compares the icon signature, and the cache
  key includes the icon file's last-modified time (the picker overwrites
  the same file on every pick, so the URI alone cannot tell a new image
  from the old one).

## [0.1.8] - 2026-11-12

### Fixed
- Image picker opened empty: MediaStore returns no images on this TV,
  so the photo grid had nothing to show. The picker is now a real file
  browser (folders first, then image files) reading the TV's storage
  directly — no media index needed. Back goes up one folder; Back at
  the root closes the picker.

## [0.1.7] - 2026-11-12

### Fixed
- Image picker grid never got focus: the adapter is set after the
  background MediaStore query, i.e. after the window's initial focus
  pass, so D-pad keys went nowhere ("Unhandled key" in logcat) and the
  user had to back out. The first thumbnail is now focused explicitly
  once the grid is laid out, with the same guard in onResume.

## [0.1.6] - 2026-11-12

### Fixed
- TizenTube is now a pinned app (the user calls it SmartTube): it pins
  at the top with Nuvio and Moonlight on fresh installs.
- Change icon did nothing: the system image picker on this TV is only a
  stub that instantly returns "cancelled". Zap now has its own built-in
  image picker (IconPickerActivity) listing the TV's own photos from
  MediaStore; the chosen image is copied into the app's private storage,
  so the icon survives reboots and permission changes.

## [0.1.5] - 2026-11-12

### Fixed
- Arrange apps ↑ ↓ ✕ and Change icon buttons unreachable: the rows
  themselves were focusable, and D-pad focus search cannot move from a
  container onto a view inside its own bounds. The rows are no longer
  focusable, so focus lands straight on the buttons.
- Flicker on every Home press: the icon cache was evicted on every
  resume (all icons blanked and reloaded one by one), and the grid was
  re-submitted twice per start (onCreate + onResume). The cache key now
  includes the custom icon URI so changed icons refresh by themselves,
  the duplicate load is gone, and an unchanged order is not re-submitted.

## [0.1.4] - 2026-11-11

### Fixed
- Settings gear STILL unreachable in 0.1.3: Leanback's VerticalGridView
  consumes DPAD keys for its own focus movement before a key listener on
  the grid ever runs. Navigation is now handled in the Activity's
  `dispatchKeyEvent` instead: Up from the top row → gear, Down from the
  gear → date → clock → grid, Centre/Enter on the gear → Settings.
- Grid jumping on every Home press: the first tile is no longer forced
  into focus on every resume, only when nothing has focus.
- Pinned apps stranded in the alphabetical tail (SmartTube) move up
  behind the pinned block on a one-time repair; the old favorites
  migration also picks up pinned apps it missed.

### Added
- Debug logging (`ZapLauncher` tag): the computed app order on every
  load, and every gear-navigation key press with its result.

## [0.1.3] - 2026-11-11

### Added
- Date & clock on the home screen are now clickable and open the system
  date & time settings.
- Arrange apps screen (replaces favorite-only ordering): move ANY app
  with ↑ ↓, hide apps with ✕. The full grid order is saved and applied.
- Change app icons: pick a custom image per app, or reset to the original.

### Fixed
- Settings gear unreachable by D-pad: pressing Up from the grid's top row
  now moves focus to the gear (with a visible focus ring), Down returns
  to the grid, and Centre/Enter opens Settings.
- Newly installed pinned apps (Nuvio TV, SmartTube, Moonlight V+) now join
  the top of the grid even when installed after the first run; previously
  favorites were only seeded once, so e.g. SmartTube stayed alphabetical.

## [0.1.2] - 2026-11-11

### Fixed
- Startup crash on the TCL UnionTV (the real cause, confirmed by logcat):
  all four built-in wallpaper drawables used `<gradient>` as their root
  element, which Android rejects at inflation time
  (`InflateException: Class not found gradient`, wrapped as
  `Resources$NotFoundException: Drawable ...:drawable/bg_default`).
  `MainActivity.onCreate` loads the wallpaper first, so the app died before
  showing any UI. Each gradient is now wrapped in
  `<shape android:shape="rectangle">`. This also fixes `app_banner.xml`,
  which references `bg_default`.

## [0.1.1] - 2026-11-11

### Fixed
- Removed `bringToFront()` from the grid's child-selected listener as a
  precaution (it can throw `IllegalStateException` while RecyclerView is
  computing a layout). NOTE: logcat later proved this was NOT the crash
  cause — the real cause was the malformed wallpaper drawables, fixed
  in 0.1.2.

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
