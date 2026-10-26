# AI Photo Editor

An Android-first personal photo assistant that helps select, enhance, and prepare photos for social media.

## Current milestone

- Native Android app using Kotlin and Jetpack Compose
- Android photo selection without broad storage permissions
- Initial project structure and automated debug APK build
- Original images remain unchanged; editing will produce separate copies

## Planned roadmap

1. Native app foundation and photo import
2. Batch photo review and technical quality scoring
3. Non-destructive automatic image adjustments
4. Before/after comparison and export
5. Subject-aware ranking and duplicate detection
6. Personal editing style and social-media crop presets

## Build

Pushes to the main branch run the GitHub Actions workflow in `.github/workflows/android.yml`. Download the `ai-photo-editor-debug-apk` artifact from a successful workflow run.

## Technology

- Kotlin, Android SDK, Jetpack Compose
- Android Photo Picker
- Gradle and GitHub Actions

Advanced AI features will be added in measured stages and tested on-device. The first release does not require a backend or Vercel.
