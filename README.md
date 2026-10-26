# FRAME / AI — Android Photo Editor

A native Android photo editor focused on batch workflows. It runs locally on the device; no website, account, backend, or Vercel deployment is required.

## Current milestone: live editing, fixed preview, and full-resolution export (Beta 0.5.0)

- Import multiple images with Android's document picker.
- Score technical quality using estimated sharpness, exposure, contrast, and clipped highlights/shadows; sort higher scores first.
- Detect byte-identical duplicate files in the selected batch and optionally remove duplicates from the current selection without deleting anything from the phone.
- Tap any photo to open the editing workspace.
- Apply style presets: Auto, Natural, Vivid, Warm, Cool, and Mono; the preview updates automatically after a short debounce.
- Adjust brightness, contrast, saturation, and warmth using sliders; changes appear live without tapping Apply.
- Center-crop to Original, Square (1:1), Portrait (4:5), Story (9:16), or Landscape (16:9), with immediate preview updates.
- Keep the preview image and main actions fixed while only the editing controls scroll; Reset edits restores Auto and Original crop.
- Preview enhanced output, switch to the original, and export one edited copy.
- Apply the chosen controls to the whole selected batch with **Enhance all**.
- Save new JPEGs under **Pictures/FRAME AI** on modern Android.
- Export processing decodes the original dimensions instead of the 1800-pixel preview size; JPEG export uses maximum quality. A selected crop ratio intentionally changes output dimensions.
- Original photos are never edited or overwritten.
- Return to the gallery to choose another photo without re-importing the batch.

## Important limitations

- Ranking scores are heuristics, not a trained AI model or an understanding of the photo's meaning. Intentional blur and artistic lighting may score poorly.
- Style presets and sliders are conventional on-device pixel adjustments, not generative editing.
- Crops are centered; there is no drag-to-reposition crop tool yet.
- The on-screen live preview uses a working size of up to about 1800 pixels for responsiveness; exports are rendered from the full-resolution source. Crop ratios intentionally reduce dimensions to the chosen framing.
- Full-resolution processing uses more memory. Very large photos may take longer or exceed device memory.
- Very large batches can take time and use battery. Keep the app open until batch export finishes.
- On modern Android, exports use MediaStore and do not require broad storage permission.

## Build and install

Pushes to `main` run GitHub Actions. Open **Actions**, select the latest successful **Android APK** run, and download the `ai-photo-editor-debug-apk` artifact. Extract the ZIP and install `app-debug.apk`.

The beta uses package `com.barshan.aiphotoeditor.beta`, labelled **FRAME AI Beta**, so it can sit alongside the earlier prototype. CI caches its debug signing key to allow later beta APKs to update in place.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Android document picker and MediaStore
- Coil for image display; AndroidX ExifInterface for orientation
- GitHub Actions, Gradle, JDK 17

## Roadmap

1. Native Android shell and multi-photo import — complete
2. Technical quality ranking — implemented; needs broader real-world validation
3. Auto enhancement and non-destructive export — implemented
4. Manual sliders, creative presets, and social crop ratios — implemented in this milestone
5. Full-resolution export and maximum-quality JPEG output — implemented in Beta 0.5.0
6. Validate edits across different phone photos; improve color/exposure algorithms
7. Exact duplicate file detection and safe removal from the selection — implemented in Beta 0.5.0
8. Near-duplicate/burst grouping and subject-aware ranking
9. Personalized style learning and smarter social export

The current editor does not claim to have a trained AI model. This milestone builds reliable controls and export behavior first; subject-aware intelligence and personalized learning come later.
