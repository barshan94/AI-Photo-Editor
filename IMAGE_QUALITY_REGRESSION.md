# Image Quality Regression Checklist

Run these checks after changing the local image reconstruction pipeline.

## Object removal

- Mark a foreground object including its outline and cast shadow.
- Remove it once and confirm the original remains unchanged.
- Run removal again without repainting; the second pass must start from the repaired preview.
- Check that the repaired region is blended into the surrounding photo without a hard rectangular/brush edge.
- Check both landscape and portrait photos. LaMa inference uses a fixed 512×512 canvas, so inspect stretched-looking context and soft details carefully.
- Undo/reset must return to the expected prior state.

## AI upscaling

- Test a smooth wall, sky, or tabletop where tile boundaries are easiest to see.
- Inspect at 100% zoom for horizontal/vertical grid lines and checkerboard texture.
- The official TensorFlow ESRGAN TFLite model receives float RGB values in the 0–255 range and returns values in the same range; do not normalize inputs to 0–1 or multiply outputs by 255.
- Overlapping ESRGAN tile predictions must be combined using edge feather weights rather than equal-weight averaging plus a post-hoc blur.
- Compare fine texture and edges against the input; do not hide seams by globally blurring the result.
- Test a portrait and a landscape photo, including an input that must be resized for memory.

## Release gate

A successful Android build only confirms compilation and tests; visual quality still requires checking the resulting APK on a real device. Do not describe either feature as fully verified until those image checks have been performed.
