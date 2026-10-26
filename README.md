# FRAME / AI — Android Photo Editor

A native Android app for ranking a batch of photos, previewing a restrained automatic enhancement, and exporting enhanced copies. It runs locally; there is no website, account, backend, or Vercel deployment.

## Current milestone: batch photo workflow

- Import multiple images with Android's document picker.
- Keep read access to selected files when Android permits it.
- Score technical quality using estimated sharpness, exposure, contrast, and clipped highlights/shadows.
- Sort photos from higher to lower technical score.
- Tap any photo for an original/enhanced preview.
- Export one enhanced copy or enhance/export the whole batch.
- Save new JPEGs under **Pictures/FRAME AI** on modern Android.
- Never modify or overwrite the original photo.

## Important limitations

- Scores are heuristics, not an AI understanding of the scene. A meaningful candid, artistic blur, or intentionally dark image can receive a low score.
- Auto enhancement is a gentle exposure/contrast/saturation adjustment, not generative editing.
- Images are decoded at a reduced working size for previews and exports, so exported files may be smaller than the original. The original remains untouched.
- Very large batches can take time and use battery. Keep the app open until batch export finishes.
- The legacy Android storage path (Android 9 and earlier) may require storage permission; modern Android versions use MediaStore.

## Build and install

Pushes to `main` run the GitHub Actions workflow. Open **Actions**, select the latest successful **Android APK** run, and download the `ai-photo-editor-debug-apk` artifact. Extract the ZIP and install `app-debug.apk` on the phone.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Android document picker and MediaStore
- Coil for image display; AndroidX ExifInterface for orientation
- GitHub Actions, Gradle, JDK 17

## Roadmap

1. Native app and multi-photo import — complete
2. Technical quality ranking — implemented in this milestone
3. Non-destructive enhancement preview and export — implemented in this milestone
4. Validate behavior on real-device photo sets and tune scoring/enhancement
5. Subject-aware ranking, duplicate/burst grouping, and smarter scene understanding
6. Personal style presets and social-media crop/export presets

The app does not yet claim to have a trained AI model. The current ranking and enhancement are on-device image-processing heuristics; later intelligence features will be added only after the baseline is tested.
