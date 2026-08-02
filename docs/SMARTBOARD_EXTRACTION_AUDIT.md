# Smart Board Extraction Audit

Audit date: 2026-08-02  
Source: `C:\Indian Servers\AIExplorer`  
Destination: `C:\Indian Servers\SMARTBoard`

This is the Phase 1, read-only baseline taken before copying production or test
code. The source project was not changed during the audit. The extraction uses
the source working tree as it existed at this baseline, including its existing
uncommitted Smart Board edits.

## Source working-tree baseline

`git -C "C:\Indian Servers\AIExplorer" status --short` reported:

```text
 M app/build.gradle.kts
 M app/src/androidTest/java/com/indianservers/aiexplorer/AIExplorerUiTest.kt
 M app/src/androidTest/java/com/indianservers/aiexplorer/smartboard/SmartBoardMultiSubjectPhase1UiTest.kt
 M app/src/main/java/com/indianservers/aiexplorer/MainActivity.kt
 M app/src/main/java/com/indianservers/aiexplorer/smartboard/presentation/SmartBoardScreen.kt
 M app/src/main/java/com/indianservers/aiexplorer/smartboard/presentation/SmartBoardViewModel.kt
 M app/src/main/java/com/indianservers/aiexplorer/ui/home/AiExplorerSplashScreen.kt
 D app/src/main/java/com/indianservers/aiexplorer/ui/home/SubjectHubScreen.kt
 M app/src/main/res/values/strings.xml
?? app/src/main/java/com/indianservers/aiexplorer/gamifymaths/
?? app/src/test/java/com/indianservers/aiexplorer/gamifymaths/
?? arengine/build/intermediates/aar_main_jar/debug/
?? arengine/build/intermediates/annotations_typedef_file/debug/
?? arengine/build/intermediates/full_jar/debug/
?? arengine/build/intermediates/incremental/debug-mergeJavaRes/
?? arengine/build/intermediates/merged_consumer_proguard_file/debug/
?? arengine/build/intermediates/merged_java_res/debug/
?? arengine/build/outputs/aar/
?? docs/GAMIFY_MATHS_MODULE_PLAN.md
?? macrobenchmark/build/
```

No source file has been deleted, moved, renamed, generated, or edited by this
extraction.

## Smart Board production inventory (37 files)

- `canvas/SmartBoardCanvasView.kt`
- `canvas/SmartBoardGeometry.kt`
- `domain/SmartBoardHistory.kt`
- `export/SmartBoardExporter.kt`
- `integration/SmartBoardMathIntegration.kt`
- `intelligence/SmartBoardContextAndRules.kt`
- `intelligence/SmartBoardIntelligenceAnalytics.kt`
- `intelligence/SmartBoardIntelligenceModels.kt`
- `intelligence/SmartBoardIntelligenceOrchestrator.kt`
- `intelligence/SmartBoardSessionMemoryCodec.kt`
- `intelligence/SmartBoardSubjectIntelligence.kt`
- `media/SmartBoardMedia.kt`
- `models/PhysicsSmartBoardModels.kt`
- `models/SmartBoardModels.kt`
- `models/SmartBoardSubjectActionModels.kt`
- `multisubject/SmartBoardMultiSubject.kt`
- `multisubject/SmartBoardMultiSubjectAnalytics.kt`
- `navigation/SmartBoardNavigation.kt`
- `persistence/SmartBoardPersistence.kt`
- `physics/PhysicsSmartBoardEngine.kt`
- `physics/PhysicsSmartBoardHandlers.kt`
- `physics/PhysicsSmartBoardTutor.kt`
- `presentation/SmartBoardScreen.kt`
- `presentation/SmartBoardViewModel.kt`
- `recognition/OfflineFormulaIdentifier.kt`
- `recognition/OfflineImageToLatexRecognizer.kt`
- `recognition/SmartBoardAdvancedRecognition.kt`
- `recognition/SmartBoardRecognition.kt`
- `recognition/SmartBoardRecognitionProduction.kt`
- `recognition/SmartBoardSemanticRecognition.kt`
- `security/SmartBoardSecurity.kt`
- `shapes/SmartBoardAutoShape.kt`
- `tools/SmartBoardDirectTools.kt`
- `tutor/SmartBoardTutor.kt`
- `tutor/SmartBoardTutorAnalytics.kt`
- `tutor/SmartBoardTutorConversationCodec.kt`
- `tutor/UnifiedSmartBoardTutor.kt`

All paths above are relative to
`app/src/main/java/com/indianservers/aiexplorer/smartboard/`.

## Unit-test inventory (16 files)

- `SmartBoardAdvancedRecognitionTest.kt`
- `SmartBoardAutoShapeTest.kt`
- `SmartBoardFinalAuditRemediationTest.kt`
- `SmartBoardFinalToolsTest.kt`
- `SmartBoardGeometryTest.kt`
- `SmartBoardHistoryPersistenceTest.kt`
- `SmartBoardInputPolicyTest.kt`
- `SmartBoardIntegrationAuditTest.kt`
- `SmartBoardMultiSubjectPhase1Test.kt`
- `SmartBoardMultiSubjectPhase3Test.kt`
- `SmartBoardOfflineFormulaAndEditingTest.kt`
- `SmartBoardPhase4IntelligenceTest.kt`
- `SmartBoardPhysicsTest.kt`
- `SmartBoardRecognitionPhases7To9Test.kt`
- `SmartBoardRecognitionTest.kt`
- `SmartBoardSemanticRecognitionTest.kt`

## Android/Compose UI-test inventory (3 files)

- `SmartBoardMultiSubjectPhase1UiTest.kt`
- `SmartBoardMultiSubjectPhase3UiTest.kt`
- `SmartBoardPhase1UiTest.kt`

Test paths are relative to the matching `app/src/test/.../smartboard/` or
`app/src/androidTest/.../smartboard/` directory.

## Imports outside the Smart Board package

### AIExplorer math/core

- `AdvancedStatisticsEngine`, `CasRow` — `core/AdvancedStatistics.kt`
- `Graph3D`, `TypedGraphEngine`, `TypedGraphExpression`,
  `TypedGraphExpressionParser`, `Vec3` and related graph models —
  `core/GraphProduction.kt`, `core/MathModels.kt`
- `MathProblemSolver` — `core/ProblemSolver.kt`
- `MathSolverTutor`, `SolverMethod` — `core/SolverIntelligence.kt`
- `Phase4Statistics` — `core/Phase4Statistics.kt`
- `SymbolicCasEngine`, `SymbolicExpression` — `core/SymbolicCas.kt`
- `TrustedMathKernel` — `core/TrustedMathKernel.kt`
- `latexStyleFormula` — top-level helper in `MainActivity.kt`

### Recognition

- `CasHandwritingRecognizer`, `CasPhotoMathRecognizer`, `MathInkPoint` —
  `input/CasMultimodalRecognition.kt`
- ML Kit Digital Ink and Latin text recognition APIs
- ONNX Runtime Android (`OnnxTensor`, `OrtEnvironment`, `OrtSession`)
- Android `org.json.JSONObject`

### Subject catalogues

- Biology: `BundledBiologyCatalogue`, biology model/repository types, with
  transitive validator and future-3D metadata types.
- Chemistry: `BundledElementData`, with transitive chemistry models and
  `ElectronConfigurationEngine`.
- Physics: formula models, repository, units, bundled formula data and formula
  validator.

The checked direct-import list did not reveal additional AIExplorer packages
beyond those above. The extraction copies the minimum transitive source files,
not the whole AIExplorer app.

## Android resources, components, permissions, assets, and native libraries

- Smart Board Kotlin code has no direct `R.*` resource reference.
- The standalone app needs its own app label, Compose-compatible theme,
  launcher/TV artwork, and backup/data-extraction XML.
- Required Android component: launcher/leanback launcher activity. Export and
  import use Storage Access Framework document contracts, so no `FileProvider`
  or broad storage permission is required.
- The Smart Board uses the system photo picker; it does not require storage
  permission. Camera hardware/permission is not directly used by the audited
  Smart Board implementation. Internet is needed by ML Kit model management.
  No Smart Board code uses audio recording.
- There is no source `app/src/main/assets` directory and no checked-in `.so`,
  `.onnx`, `.ort`, or `.tflite` file used by Smart Board.
- Offline image-to-LaTeX looks for a user-installed model under
  `filesDir/offline_models/texteller-q4-v2`; absence is handled explicitly by
  the recognizer.
- Media is stored beneath `filesDir/smartboard-assets`. Share outputs use
  `cacheDir/shared-maths`.
- Native libraries are supplied transitively by ONNX Runtime and packaged by
  Gradle per ABI.

## Manifest and Gradle requirements

- `compileSdk 36.1`, `targetSdk 36`, `minSdk 31`, Java/Kotlin JVM 17.
- Compose Material 3, Compose Foundation, Activity Compose, Navigation
  Compose, lifecycle ViewModel Compose, Preferences DataStore.
- ML Kit text recognition, ML Kit digital ink recognition, ONNX Runtime
  Android.
- JUnit 4, AndroidX Test JUnit/Espresso, Compose UI test JUnit4.
- Optional hardware declarations for touchscreen and Leanback allow phone,
  tablet and TV installation.
- ABI outputs: `arm64-v8a`, `armeabi-v7a`, and `x86_64`, with no universal
  debug APK.

Material APK-size contributors are ONNX Runtime (native libraries), ML Kit
Digital Ink (download/runtime code and model manager), and ML Kit text
recognition. Compose and Kotlin runtime are secondary contributors.

## Persistence and compatibility surface

- SQLite database tables: `boards`, `recovery`, `intelligence_sessions`, and
  `tutor_conversations`.
- Document codec supports schema migration from versions 0 and 1 to the
  current version and returns structured errors for unsupported/corrupt input.
- AIExplorer DataStore name: `smart_board_preferences`.
- Keys: `input_mode`, `pressure`, `smoothing`, `high_contrast`,
  `reduced_motion`, `recognition_mode`, `recognition_defaults_version`,
  `auto_shape_enabled`, `auto_shape_delay`, `intelligence_mode`,
  `intelligence_suggestions`, `recognition_personalization`,
  `recognition_diagnostics`, `recognition_quality_tier`, and
  `recognition_personalization_profile_v1`.
- The standalone app must use a different DataStore filename while retaining
  key semantics.

## References from outside Smart Board

`MainActivity.kt` is the only production file outside the package with direct
Smart Board integration. It imports `SmartBoardFeatureRoot`, stores
`showSmartBoard` and `returnToSmartBoard` navigation state, exposes
`openSmartBoard`, `openSmartBoardMathModule`, and
`returnFromSmartBoardMathModule`, handles back navigation, renders the feature,
and supplies callbacks for Graph 2D, Graph 3D, Geometry 2D, Geometry 3D,
Statistics/verification-related flows, and physics workspaces.

The source UI tests and app-level tests also reference Smart Board; these must
remain in AIExplorer during this extraction.

## AIExplorer handoffs to replace

- Graph 2D (`graph2d`)
- Graph 3D (`graph3d`)
- Editable Geometry 2D (`geometry2d`)
- Geometry 3D (`geometry3d`)
- Physics circuit, wave, and optics workspaces
- Statistics actions
- Work verification and tutoring flows where initiated from the board

The standalone launcher must route these to working in-app destinations. Empty
default callbacks on `SmartBoardFeatureRoot` are not acceptable as standalone
integration.

## Standalone resource inventory

Resources required by the extracted app are:

- `values/strings.xml`, `values/colors.xml`, `values/themes.xml`, and the
  corresponding night theme;
- adaptive launcher definitions and foreground/background vector/color
  resources;
- `drawable/tv_banner.xml` for Leanback launchers;
- `xml/backup_rules.xml` and `xml/data_extraction_rules.xml`.

The generated Android Studio shell also contains unused menu, navigation,
dimension, avatar-vector, and density launcher fallback resources. None is
referenced by Smart Board production Kotlin. There are no checked-in model
assets or native libraries; ML Kit and ONNX Runtime supply their runtime
payloads through Gradle dependencies.

## Audit conclusion at the Phase 1 baseline

The 37-file package is internally cohesive after migrating the minimum
transitive math and subject engines. `MainActivity.kt` is the sole production
integration point outside the package. The extraction did not write source
files.

After the successful AIExplorer debug build, a concurrent external change
removed the complete Smart Board production/test packages,
`input/CasMultimodalRecognition.kt`, and other unrelated source/resources from
the AIExplorer working tree. It also changed `MainActivity.kt`,
`app/build.gradle.kts`, `gradle/libs.versions.toml`, and other tracked files.
This does not match the read-only audit baseline and was not performed or
reverted by the extraction. The current post-change AIExplorer Kotlin
compilation still succeeds, but the original Smart Board source is no longer
present in that working tree.
