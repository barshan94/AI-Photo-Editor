# FRAME AI — Product Vision and Additive Roadmap

## Product promise

FRAME AI is an Android photo editor that combines dependable everyday editing with optional, scene-aware AI assistance. It must not become an AI-only editor, and AI must never be forced on users who simply want to select their best photos, crop, use a preset, or export.

**All new intelligence is additive. Existing workflows and features must remain available unless a change is explicitly requested and shown to be necessary.**

## Product pillars

### 1. Classic editing and photo selection — preserve

Keep the existing photo import, technical quality scoring, exact and visual-similarity detection, burst candidate grouping, keep-best comparison, Keep / Review / Reject decisions, crop controls, presets, manual adjustments, before/after comparison, undo/redo, crop guides, full-resolution export, and batch processing.

- Do not remove or silently change these workflows when introducing AI.
- Do not force an AI analysis step before ordinary editing or exporting.
- Keep original photos unchanged and preserve non-destructive editing.
- Make new processing optional, with clear progress, cancellation, and error recovery.

### 2. AI Photo Analysis — new optional capability

Offer an explicit Analyze Photo action. When supported, analyze scene type and content, including single person, couple, group, portrait, landscape, event, object, pet, foreground/background, faces, hair, clothing, sky, and lighting conditions.

Return a structured diagnosis with confidence/uncertainty where possible. Separate detected facts from suggestions; do not pretend the model knows the photographer's intent with certainty.

### 3. Adaptive enhancement — decisions before edits

Build an editing planner that proposes only useful changes. It should consider exposure, highlights/shadows, white balance, color casts, contrast, sharpness, noise, local lighting, and scene context.

- A good region should be left alone.
- A face correction must not unintentionally alter the background.
- A background correction must protect people and important foreground objects.
- Group photos should allow region-specific decisions for different people when technically feasible.
- Every suggested edit should be reviewable, adjustable, and reversible.
- Preserve natural appearance and identity; avoid aggressive beauty changes by default.

### 4. Region-aware editing — add progressively

Develop reusable masks and selection tools for person, individual person, face, skin, hair, clothing, background, sky, and selected objects. Show mask/selection previews and provide manual correction when automatic segmentation is uncertain. Mask edges and protected regions must be tested before an operation is considered reliable.

### 5. Specialized AI tools — optional, separate actions

Add advanced tools as independently selectable capabilities, not mandatory steps in a universal filter:

- Face and skin retouching, subtle smoothing, blemish correction, optional makeup and local face lighting.
- Person-only or background-only enhancement and selective relighting.
- Object and unwanted-person removal with background reconstruction.
- Upscaling, denoising, detail recovery, and restoration of degraded images.
- Additional scene-specific workflows as models and device performance allow.

Do not advertise a tool as complete until its actual model, limits, performance, and quality have been tested. Segmentation alone is not generative object removal or full scene understanding.

### 6. Edit verification and user trust

Before export, allow users to compare original and result, inspect changes, and undo or reject them. Validate mask boundaries, unintended changes to protected regions, natural skin texture, detail preservation, image dimensions, and export integrity. Communicate when a feature falls back, is unavailable, or is uncertain.

## UX principles

- Classic editing remains easy to reach.
- AI Photo Studio is an optional entry point; users can skip it and continue editing normally.
- Offer a concise diagnosis and proposed edit plan before expensive or destructive-looking operations.
- Use separate controls for Analyze, Apply selected suggestions, and individual advanced tools.
- No automatic upload of personal photos. Any future cloud processing must be explicit opt-in, with clear privacy, cost, and connectivity disclosure.
- Keep original files untouched. Save edited copies and make AI changes reversible within the editing session wherever practical.
- Be honest about model limitations and avoid promising perfect masks, identity preservation, or artifact-free removal.

## Implementation order

1. Validate Beta 2.1.0 on real photos and preserve the existing editing baseline.
2. Add a testable scene-analysis result model and an optional Analyze Photo UI, initially using dependable on-device signals and explicitly supported models.
3. Add a region/mask abstraction with visual overlays and tests for mask dimensions, alignment, edges, and protected areas.
4. Add a selective enhancement planner that produces an inspectable per-region edit plan and can choose to make no change.
5. Integrate one region-aware correction at a time, with before/after and regression testing.
6. Evaluate specialized models for face retouching, object/person removal, and upscaling separately; choose on-device or optional cloud processing based on quality, privacy, speed, memory, and cost.
7. Run real-device tests, fix regressions, and prepare a release only after existing and new workflows pass.

## Definition of done for every new feature

- Existing classic workflows still work.
- The new feature is optional and can be skipped.
- Original files remain untouched.
- Failure, cancellation, and low-confidence cases are handled clearly.
- Unit/regression tests cover deterministic logic; visual tests use representative real photos.
- Performance and memory behavior are measured on supported Android devices.
- Claims in the UI and documentation match what the implementation can actually do.
