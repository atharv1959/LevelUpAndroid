# Level Up — Android app

A native Android starter app built with Kotlin and Jetpack Compose.

## Included
- Daily push-up target (editable)
- Log push-ups, squats, sit-ups, and other counted workouts
- Today dashboard and progress bar
- Workout history grouped by date
- Streak count, XP, levels, total reps, sessions, and per-exercise personal records
- Mark a day as a rest day
- Local-only persistence using Android SharedPreferences
- Reset data from Settings
- GitHub Actions workflow to compile a debug APK

## Build the APK without Android Studio
1. Create a new GitHub repository.
2. Upload all files/folders in this project to the repository root.
3. Open the repository's **Actions** tab.
4. Select **Build Level Up APK**, then choose **Run workflow** (or push to `main`).
5. When the workflow finishes successfully, open its run and download the `Level-Up-debug-APK` artifact.
6. Extract the downloaded ZIP and install `app-debug.apk` on your Android phone. If prompted, allow installing apps from your browser/file manager.

## Important
- This ZIP contains project source and an automated cloud-build workflow, **not a compiled APK**.
- The first cloud build downloads Android/Gradle dependencies and may take several minutes.
- This debug APK is unsigned for Play Store release, but is suitable for personal testing.
- Data is stored locally on the device and is not synced to the cloud.
- Rest days count toward the current streak calculation; if you want workout-only streaks, adjust `calculateStreak()` in `MainActivity.kt`.
- The app currently has no push notifications or account/cloud sync.
