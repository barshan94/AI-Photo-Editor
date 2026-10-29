# FRAME AI model integration notes

## On-device models

- **MediaPipe Selfie Multiclass (256×256)** — downloaded on first portrait/skin-mask use from Google's public model endpoint into private app storage. Its classes are background, hair, body skin, face skin, clothes, and accessories. Used by MulticlassSegmentationEngine for portrait/background separation and a distinct skin mask.
- **ESRGAN TensorFlow Lite** — downloaded from TensorFlow's official TFLite example model URL. AiUpscaler runs 50×50 RGB tiles and stitches the model's 4× output. To limit memory use, input is resized to at most 1024 pixels on the long edge / 1 megapixel before 4× inference.
- **LaMa ONNX int8, fixed 512×512** — downloaded on first object-removal use from the public g-ronimo/lama model repository into private app storage. LamaInpainter accepts a user-painted mask, runs local inpainting, then composites the repaired region over the source. The model repository declares Apache-2.0 licensing.

ESRGAN remains bundled in the APK. The larger LaMa and MediaPipe models are downloaded once on first use and kept in the app's private storage. The two downloads require an internet connection; subsequent inference uses the local files. Photos remain on-device and are never uploaded.

## Where to use them

1. Open a photo. Opening it still starts on Original and does not auto-edit it.
2. Choose AI Portrait for a visible starting level of background blur, then adjust Background blur below.
3. Use Darker skin areas · adaptive lift to lift shadowed skin more strongly (for example, hands). Use Overall skin lightening independently to balance detected face/body skin. Skin softening applies a restrained blur only inside the skin mask, and Natural makeup uses ML Kit face contours for optional lip/cheek tint. Skin-only lighting and softening are disabled if the multiclass skin mask model fails rather than brightening the whole person.
4. Under the AI toolbox, tap Mark object and paint red over the unwanted area, including its shadow. Pinch with two fingers to zoom/pan while painting with one finger. Tap Remove marked area to run LaMa. The repaired result stays in the preview for further passes; use Export or Share to save the final copy.
5. Tap Run AI Upscale 4× for ESRGAN super-resolution. For memory safety, large input images are reduced before inference; the source photo is never overwritten.

## Important limits

- These are pretrained inference models, not models trained from the user's photos. Personal Edit Learning only remembers accepted slider preferences locally; it does not train neural-network weights.
- Portrait segmentation can miss fine hair, fingers, occluded skin, or unusual poses. Inspect the preview before exporting.
- LaMa uses a fixed 512×512 inference canvas, so reconstructed areas may be softer than the original at full resolution. Mark the full object and its shadow for the best result.
- ESRGAN creates a 4× result from the memory-bounded input. When the input is reduced for memory safety, it is not a true 4× enlargement of the full original-resolution source.
- Photos are processed on-device. First-time LaMa object removal and multiclass portrait/skin segmentation require downloading their model files; the files are cached privately for later offline use. No photo is uploaded for inference.
