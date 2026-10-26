# FRAME / AI — Android Photo Editor

A native Android photo editor focused on batch workflows. It runs locally on the device; no website, account, backend, or Vercel deployment is required.

## Current milestone: export reliability and regression tests (Beta 1.8.0)

- Batch preset settings, progress labels, and completion summaries are extracted into a testable policy module.
- Automated unit tests cover supported presets, expected adjustment values, progress bounds, summary counts, and failure-detail limits.
- GitHub Actions runs `gradle testDebugUnitTest` before assembling and uploading the APK.
- Coroutine cancellation is rethrown rather than incorrectly reported as a normal export failure.
- Original photos remain untouched; exported JPEG copies go to Pictures/FRAME AI.

## Previous milestone: reviewed batch editing and export (Beta 1.7.0)

- Choose a batch-wide preset: Current edits, Auto, Natural, Vivid, Warm, Cool, or Mono.
- Preview a representative eligible photo with the chosen batch preset before exporting.
- Review how many photos will be exported and how many marked Reject will be skipped.
- Export progress updates as each photo is processed; a completion summary reports saved, failed, and skipped counts plus up to five failed items.
- Exports remain full-resolution JPEG copies in Pictures/FRAME AI; source photos are never overwritten.

## Previous milestone: professional editing controls (Beta 1.6.0)

- Before/after comparison view with an adjustable reveal slider.
- Undo and redo for editing settings, including style, tone, and crop controls.
- Rule-of-thirds crop guides can be toggled on and off.
- Existing presets, crop positioning, and non-destructive export remain available.
- Comparison and guide overlays are preview-only; they are not burned into exported images.

## Previous milestone: photo triage and decision filters (Beta 1.5.0)

- Give each photo a personal **Keep**, **Review**, or **Reject** decision from the editor; the default is Review.
- Filter the grid by decision, visually similar/exact duplicates, or burst candidates, and see live counts for each decision.
- Batch **Enhance all** skips photos marked Reject. Individual export remains available if you open a rejected photo deliberately.
- View the technical score breakdown (sharpness, exposure, and contrast) while editing. Scores remain guidance, not a substitute for personal judgement.
- Decisions only change the current in-app batch; they do not delete, overwrite, or modify original images.

## Previous milestone: safer burst-candidate grouping (Beta 1.4.0)

- Import multiple images with Android's document picker.
- Score technical quality using estimated sharpness, exposure, contrast, and clipped highlights/shadows; sort higher scores first.
- Detect byte-identical duplicate files and visually similar frames in the selected batch; similar-shot candidates are ranked by technical score. The highest-scoring representative is retained by the optional cleanup action. No files are deleted from the phone.
- Read capture time from MediaStore or EXIF when available; photos whose entire candidate group fits within a two-second capture-time span are marked as **burst candidates**. This prevents a chain of individually close timestamps from merging a much longer sequence. An optional action keeps the highest technical score per candidate group and removes the rest from the current selection only.
- Choose any detected burst-candidate group from a list showing capture time, photo count, and best technical score; compare its two highest-ranked photos side by side, then open either photo for editing. Comparison itself never removes or deletes photos.
- Tap any photo to open the editing workspace.
- Use Previous / Next to move through the selected batch without returning to the grid; the position indicator shows the current photo.
- Use the editor's **All photos** button or Android system Back to return to the existing batch without re-importing it.
- Apply style presets: Auto, Natural, Vivid, Warm, Cool, and Mono; the preview updates automatically after a short debounce.
- Adjust brightness, contrast, saturation, and warmth using sliders; changes appear live without tapping Apply.
- Crop to Original, Square (1:1), Portrait (4:5), Story (9:16), or Landscape (16:9), then drag the preview to reposition the crop window; horizontal and vertical sliders remain available for fine adjustment.
- Pinch to zoom the preview up to 5×, drag to inspect details, and reset zoom with one tap.
- Keep the preview image and main actions fixed while only the editing controls scroll; Reset edits restores Auto and Original crop.
- Preview enhanced output, switch to the original, and export one edited copy.
- Apply the chosen controls to the whole selected batch with **Enhance all**.
- Save new JPEGs under **Pictures/FRAME AI** on modern Android.
- Export processing decodes the original dimensions instead of the 1800-pixel preview size; JPEG export uses maximum quality. A selected crop ratio intentionally changes output dimensions.
- Original photos are never edited or overwritten.
- Return to the gallery to choose another photo without re-importing the batch.

## Important limitations

- Ranking scores are heuristics, not a trained AI model or an understanding of the photo's meaning. Intentional blur and artistic lighting may score poorly.
- Burst candidates are inferred from recorded capture timestamps, not definitive camera burst metadata. The whole group is limited to a two-second timestamp span, but photos taken close together may still not belong to the same camera burst; photos without readable capture time cannot be grouped.
- Style presets and sliders are conventional on-device pixel adjustments, not generative editing.
- When a crop ratio is active, dragging the preview adjusts crop position and disables pinch-zoom gestures for that preview. Switch to Original to use pinch-to-zoom and pan.
- The on-screen live preview uses a working size of up to about 1800 pixels for responsiveness; pinch-to-zoom magnifies this preview, not the exported source. Exports are rendered from the full-resolution source. Crop ratios intentionally reduce dimensions to the chosen framing.
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
6. Pinch-to-zoom and pan in the editor preview — implemented in Beta 0.6.0
7. Preserve selected batch when using Android Back; previous/next photo navigation — implemented in Beta 0.7.0
8. Reposition crop window horizontally and vertically — implemented in Beta 0.8.0
9. Validate edits across different phone photos; improve color/exposure algorithms
10. Exact duplicate file detection and safe removal from the selection — implemented in Beta 0.5.0
11. Visual-similarity grouping and safe removal from the selection — implemented in Beta 0.6.0; heuristic needs real-world validation
12. Capture-time-based burst candidate grouping and safe keep-best action — implemented in Beta 1.0.0; needs validation across device metadata formats
13. Choose a burst-candidate group and compare its top two photos side by side — implemented in Beta 1.2.0; needs usability testing on-device
16. Direct drag-to-position crop window with slider fine adjustment — implemented in Beta 1.3.0; needs usability testing on-device
17. Prevent over-grouping burst candidates caused by chains of adjacent timestamps — implemented in Beta 1.4.0; needs validation on real photo batches
18. Per-photo Keep / Review / Reject triage, decision filters, batch export skipping rejected items, and score breakdown — implemented in Beta 1.5.0; needs usability testing on-device
19. Before/after reveal slider, edit undo/redo, and toggleable rule-of-thirds crop guides — implemented in Beta 1.6.0; needs usability testing on-device
20. Batch-wide preset selection, sample preview, progress and export summary — implemented in Beta 1.7.0; needs real-device usability testing
21. Regression tests for batch presets/progress/summary and CI test gate — implemented in Beta 1.8.0; device export stress-testing remains
14. Subject-aware ranking and personalized style learning
15. Personalized style learning and smarter social export

The current editor does not claim to have a trained AI model. This milestone builds reliable controls and export behavior first; subject-aware intelligence and personalized learning come later.
