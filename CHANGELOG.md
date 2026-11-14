# Changelog

## 3.0.0
- Added SAF-based scan of a user-selected folder for temporary-looking files and empty folders.
- Added per-item selection for media and selected-folder cleanup.
- Added a shortcut to Android's storage management screen.
- Clarified Android 15 non-root restrictions; no all-files access or root permission requested.
- Updated version code/name to 3 / 3.0.0.


## 5.0.0
- Added a redesigned shield-and-energy app icon and refreshed home dashboard.
- Added a dedicated Settings screen with System, Light and Dark themes plus System, English and Persian language choices.
- Added Schedule Clean Up with recurring 12, 24 or 48-hour review reminders using WorkManager. Reminders never delete files.
- Added a dedicated Installed Apps entry point with search and links to Android app settings.
- Expanded selected-folder residual scanning recursion depth. Users can select a broad shared-storage folder through Android's Storage Access Framework.
- Preserved the existing storage overview, large-media review, selected-folder scanner, storage monitor and Speed Boost features.
- Updated version to 5.0.0 (versionCode 5).

## Platform limitations
- The residual scanner only scans folders explicitly granted by Android's folder picker. Android 11+ blocks ordinary apps from protected Android/data and other apps' private data.
- Installed Apps lists apps visible to this app through Android package visibility; it cannot promise a complete list of every package on every Android version.
- Scheduled cleanup is a reminder to scan and review. It does not automatically delete files or clear another app's cache.
