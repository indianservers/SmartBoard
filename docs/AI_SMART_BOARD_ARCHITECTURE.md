# AI SMART Board Architecture

Status: Phase 1 foundation implemented on 2026-08-05.

## Three-phase delivery plan

### Phase 1 — Foundation and architecture

Status: **IMPLEMENTED**

- Inspect the existing canvas, document, recognition, graph, intelligence, history, persistence,
  navigation and test layers.
- Preserve the production recognition pipeline and the raw-stroke source of truth.
- Define immutable `BoardIntelligenceState`, board modes, structured object contracts, semantic
  relations, ambiguity/error/suggestion models, dirty regions and versioned result acceptance.
- Add opt-in AI feature flags. All incomplete capabilities are disabled in production defaults.
- Add a reusable, vector-only breathing robot assistant face with reduced-motion support.
- Do not connect the new state or robot to the user-facing board yet.

### Phase 2 — Live structured board intelligence

Status: **NOT IMPLEMENTED**

- Adapt existing `SmartBoardElement` and recognition results into stroke-linked `BoardObject`s.
- Introduce a document-scoped state store/repository without duplicating the board document.
- Track dirty regions after stroke completion and reprocess only intersecting groups.
- Debounce recognition, assign request/region versions, cancel stale work and reject late results.
- Link equations, graphs, labels, diagrams, solution steps and generated explanations.
- Add semantic selection, localized ambiguity correction and undoable AI actions.
- Connect the robot assistant and a non-blocking suggestion rail behind feature flags.
- Preserve existing undo, redo, erasing, selection, zoom, pan, autosave and export behavior.

### Phase 3 — Synchronized graph and teaching workspace

Status: **NOT IMPLEMENTED**

- Add first-class native Graph Mode using the same document and structured objects.
- Implement an expression panel, Cartesian 2D graphing, zoom, pan, auto-fit, multiple functions,
  roots, intersections and board-to-graph synchronization.
- Add sliders, inspector tools, derivatives, integrals, asymptotes, piecewise/polar/parametric
  support only after stable 2D behavior.
- Add hand-drawn graph analysis, equation/graph comparison, challenge scoring and validation.
- Add assistant commands, lesson classification, document-scoped memory and lesson outputs.
- Complete phone/tablet, rotation, background/foreground, touch/stylus and performance tests.

## Existing architecture inspected

### Canvas and strokes

- `SmartBoardCanvasView` is the Android drawing surface.
- `StrokeElement` and `StrokePoint` preserve document-space vector ink, pressure and timestamps.
- `SmartBoardBounds`, `SmartBoardViewport`, `SmartBoardCoordinates` and
  `SmartBoardStrokeGeometry` provide spatial behavior.
- `SmartBoardDocument.elements` is the authoritative ordered board content.
- Source ink is retained when fitted shapes or recognized expressions are created.

### State and presentation

- `SmartBoardViewModel` owns the active `SmartBoardDocument`, selection, tools, preferences,
  recognition review, semantic canvas, graph suggestions, tutor state and background jobs.
- Presentation uses Jetpack Compose in `SmartBoardScreen.kt`; the canvas itself is hosted as an
  Android `View`.
- State currently uses Compose `mutableStateOf` with a unidirectional UI-to-ViewModel flow.
- Dependencies are constructed directly/lazily in the ViewModel; no DI framework is installed.

### Recognition pipeline

The production path is retained:

```text
SmartBoardCanvasView
  -> StrokeElement / StrokePoint
  -> SmartBoardViewModel.recognizeSelection()
  -> MathRecognitionRequestBuilder
  -> ML Kit digital ink + offline image recognition
  -> MultimodalMathRecognitionEngine
  -> TexTeller Q4 / ML Kit image evidence
  -> contextual candidate reranking
  -> SmartBoardSemanticExpressionBuilder
  -> MathRecognitionResult / MathExpressionElement
```
- `MathRecognitionResult` preserves raw LaTeX, normalized expression, confidence, alternatives,
  detected type and warnings.
- Recognition review is user-controlled; uncertain candidates are not silently substituted.
- Recognition, raster generation and tutor work already use coroutines/background dispatchers.
- Existing jobs cover recognition, streaming recognition, shapes, canvas intelligence, semantic
  canvas, graph intelligence and tutor work.

### Existing semantic and graph foundations

- `SmartBoardSemanticCanvasEngine` already creates semantic nodes/edges and supports search,
  semantic lasso and contextual snapping.
- `SmartBoardCanvasIntelligenceEngine` already exposes intent groups, hypotheses, uncertainty
  regions and teach-board profiles.
- `SmartBoardMathGraphIntelligenceEngine` already provides equivalence checks, parameter discovery,
  graph-from-ink candidates, mistake localization and spatial hints.
- `GraphConfigurationElement` stores editable graph definitions and source element links.
- Existing graph infrastructure is useful but is not yet the dedicated synchronized workspace
  defined by this plan.

### History and undo

- `SmartBoardCommandHistory` is the existing undo/redo boundary.
- Recognition insertion, replacement, movement, grouping, visibility changes and board clearing
  already use reversible commands.
- Future AI writes must be implemented as new `SmartBoardCommand`s or compositions of existing
  commands. Suggestions remain read-only until accepted.

### Persistence

- `SmartBoardDocumentCodec` stores the versioned document; current schema version is 8.
- `SmartBoardRepository` persists boards, recovery state, intelligence sessions, tutor
  conversations and preferences using SQLite/DataStore.
- Lesson intelligence is already scoped by board ID. Phase 2 must extend this approach rather than
  storing global or cross-lesson state.

### Navigation

- The current Smart Board is hosted by `MainActivity` through `SmartBoardFeatureRoot`.
- `SmartBoardRoute` currently exposes only the board route.
- Phase 3 should add Graph Mode navigation while passing document/object IDs, not screenshots or
  copied board documents.

### Existing test architecture

- Local JVM tests cover recognition, semantic canvas, graph intelligence, history, persistence,
  geometry, commands, tutoring and subject intelligence.
- Android instrumentation tests cover physical graph UI, handwriting corpora, recognition,
  shapes, sharing and multi-subject flows.
- The comprehensive audit and its evidence remain separate debug/test infrastructure.

## New Phase 1 contracts

### Board intelligence state

`BoardIntelligenceState` is an immutable semantic projection over the existing document. It contains:

- Pages and active mode
- Selected structured objects
- Stroke-linked recognized objects
- Semantic relations
- Document-scoped lesson context
- Local recognition ambiguities
- Localized mathematical errors
- Ranked, dismissible suggestions
- Dirty regions and versioned processing state

It is deliberately not embedded into `SmartBoardDocument` in Phase 1. No schema migration or
behavioral change is needed until the adapter and persistence strategy are proven in Phase 2.

### Object identity and provenance

Every `BoardObject` has:

- Stable object and page IDs
- Document-space bounds
- Original source stroke IDs
- Optional confidence
- Creation/update timestamps
- Source revision

Generated graph or explanation objects reference their source objects through `BoardRelation`s.
They never impersonate handwritten ink.

### Incremental and stale-result policy

Every affected region receives its own version in addition to the document content version.
A recognition result may be applied only when:

```text
document ID matches
AND active page ID matches
AND content version matches
AND dirty-region version matches
```

Cancellation reduces wasted work; version validation is the correctness boundary when a provider
cannot be cancelled promptly.

### Feature flags

`AiSmartBoardFeatureFlags.ProductionDefault` enables nothing. Phase 2 and Phase 3 features must be
enabled independently, allowing tests and controlled rollout without weakening current behavior.

### Vector robot assistant

`SmartBoardRoboAssistantFace` is a Compose `Canvas` drawing:

- No bitmap, WebView or external asset
- Consistent robot face across idle, listening, thinking, speaking and attention states
- Subtle breathing/glow animation
- Reduced-motion mode
- Screen-reader content description
- No assistant action, cloud request or board mutation in Phase 1

## Safety and regression boundaries

- Original strokes remain authoritative.
- No existing recognition normalization was removed or weakened.
- No main-thread recognition work was introduced.
- No persistence schema was changed.
- No navigation or user-facing board behavior changed.
- No cloud transport was introduced.
- Every future AI mutation must enter the existing undo history.
- Ranked alternatives and localized ambiguity must remain visible when confidence is insufficient.

## Phase 1 completion report

| Requirement | Status |
|---|---|
| Files inspected | **TESTED** |
| Existing architecture documented | **IMPLEMENTED** |
| Three-phase plan documented | **IMPLEMENTED** |
| Board intelligence contracts | **IMPLEMENTED** |
| Structured object type contracts | **IMPLEMENTED** |
| Dirty-region/version contracts | **IMPLEMENTED** |
| Feature flags default-off | **IMPLEMENTED** |
| Vector robot assistant shell | **IMPLEMENTED** |
| Robot connected to live UI | **NOT IMPLEMENTED** |
| Live structured-object adapter | **NOT IMPLEMENTED** |
| Dedicated Graph Mode | **NOT IMPLEMENTED** |
| Lesson memory extensions | **NOT IMPLEMENTED** |
| Performance change | **TESTED** — no runtime path enabled |
| Manual UI verification | **NOT IMPLEMENTED** — component is intentionally not mounted |

## Phase 1 build and test commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests `
  "com.indianservers.smartboard.smartboard.BoardIntelligenceStatePhase1Test"

.\gradlew.bat :app:assembleDebug
```
