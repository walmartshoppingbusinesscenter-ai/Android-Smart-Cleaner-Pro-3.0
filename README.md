# Android Smart Cleaner Pro 3.0

Android 15-friendly storage review app built with Kotlin and Jetpack Compose.

## Features
- Storage overview (used/free/total).
- Large shared photos and videos scanner (20 MB+).
- Per-item selection before Android's system-confirmed media deletion flow.
- User-selected folder scanner via Android Storage Access Framework. It looks for temporary-looking file extensions/names and empty folders only within the folder the user explicitly grants.
- Manual deletion of selected items in the chosen folder.
- Opens Android's storage manager for system-provided storage cleanup options.
- Light/dark theme and Persian/English language selection.

## Important Android limitations
- No root access is assumed or requested.
- The app does not request `MANAGE_EXTERNAL_STORAGE`.
- It cannot access other apps' private internal data/cache or protected `Android/data` and `Android/obb` directories.
- It cannot guarantee finding every leftover from uninstalled apps.
- It does not silently clear system/app caches. The storage-settings shortcut only opens Android's own storage-management UI; any cleanup is controlled by Android and the user.
- The folder scanner's matches are heuristics, not proof that a file is junk. Review every item before deletion. Do not select files you do not recognize.
- No files are deleted automatically and no files are uploaded.

## Build
Open this project in Android Studio, or use a configured GitHub Actions workflow to run `assembleDebug`.
The generated debug APK is normally at `app/build/outputs/apk/debug/app-debug.apk`.

## Version
- versionCode: 3
- versionName: 3.0.0

## License
MIT
