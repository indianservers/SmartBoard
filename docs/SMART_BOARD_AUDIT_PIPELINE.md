# Smart Board Recognition Audit: Existing Pipeline and Safe Insertion Points

Inspection date: 2026-08-05

## Existing production pipeline

| Concern | Existing implementation |
|---|---|
| Canvas | `SmartBoardCanvasView` renders and captures document-space ink while preserving pan/zoom transforms. |
| Stroke model | `StrokeElement` contains ordered `StrokePoint` values with position, pressure, timestamp, tool, width, opacity, color, bounds and stable id. |
| Recognition trigger | `SmartBoardViewModel.recognizeSelection()` collects visible selected strokes, builds a request, renders raster evidence and invokes the configured recognition service off the UI thread. |
| Input format | `MathRecognitionInput` carries the original digital strokes, selection bounds, rendered PNG and request fingerprint. |
| Production engine | `MultimodalMathRecognitionEngine` fuses `MlKitMathRecognitionAdapter` digital ink with `DedicatedOfflineImageMathRecognitionAdapter`; the latter uses the cached offline TexTeller model and existing ML Kit image fallback. |
| Result model | `MathRecognitionResult` exposes raw/display LaTeX, normalized expression, plain text, confidence, ranked alternatives, detected expression type and warnings. |
| Structural processing | `StructureAwareRecognitionEnhancer`, `SmartBoardSemanticRecognitionEngine` and `SmartBoardLatexAdapter` provide spatial recovery, semantic trees and engine-form conversion. |
| Graphs | `SmartBoardMathGraphIntelligenceEngine.analyzeInk()` detects axes/curve strokes and returns fitted editable graph candidates; typed graph adapters provide drawable output. |
| Shapes | `DeterministicAutoShapeRecognizer` returns ranked `AutoShapeCandidate` values with type, confidence, bounds, rationale and source-stroke ids. |
| Existing diagnostics | Bounded recognition diagnostics record input path, latency bucket, confidence bucket, candidate count and corrections without storing user ink. |
| Existing tests | JVM tests cover parsing/intelligence; Android instrumentation tests exercise production digital-ink/image recognition, graphs and hand-drawn 2D/3D shapes. |
| Debug configuration | The Android `debug` build is non-minified; `androidTest` targets the debug application. Release is minified and resource-shrunk. |

## Audit insertion design

The audit is isolated to `app/src/debug/.../audit` and `app/src/androidTest/.../audit`.
Nothing under `app/src/main` is modified. Release builds therefore contain no audit screen,
dataset, evidence writer or batch runner.

Automated and hybrid tests build the same `MathRecognitionInput` used by the board and call
the same production `MultimodalMathRecognitionEngine`. Graph and geometry cases call their
existing production engines. Raw provider output is persisted before audit normalization.

The audit never changes recognition candidates. Its normalization and scoring are comparison-only
and retain separate literal, semantic, structural and spatial metrics.

## Safety findings

- Audit batches run in instrumentation coroutines, not on the main UI thread.
- All recognition remains on-device and uses the app's existing offline model configuration.
- Evidence is append-only in a timestamped run directory.
- Undo, redo, erase, clear, selection, pan, zoom, export and normal recognition code paths are not changed.
- The manual audit activity exists only in the debug manifest. It is launchable for debug testing and is absent from release builds.
