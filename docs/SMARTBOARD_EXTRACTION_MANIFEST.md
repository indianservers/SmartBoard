# Smart Board Extraction Manifest

Extraction date: 2026-08-02  
Source: `C:\Indian Servers\AIExplorer`  
Standalone project: `C:\Indian Servers\SMARTBoard`

## Completion status

The standalone application compiles, packages, and passes all migrated local
unit tests. It is not declared independently verified because the connected
physical device remained behind its lockscreen and all Compose instrumentation
tests were prevented from observing an activity hierarchy. No assertions were
weakened. Manual interaction scenarios therefore remain pending on an unlocked
phone/tablet/TV or emulator.

AIExplorer was untouched by this extraction and its `:app:assembleDebug` task
succeeded against the audited source. Afterward, a concurrent external change
deleted the Smart Board source/tests and changed other tracked files. Those
changes were not reverted because doing so could overwrite another actor's
work. AIExplorer `:app:compileDebugKotlin` also succeeds in that new state.

## Production source mapping

Every production file below maps from:

`AIExplorer/app/src/main/java/com/indianservers/aiexplorer/smartboard/<path>`

to:

`SMARTBoard/app/src/main/java/com/indianservers/smartboard/smartboard/<path>`

with package/import prefix
`com.indianservers.aiexplorer` changed to `com.indianservers.smartboard`.

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

Standalone-only changes to migrated sources:

- Preferences DataStore: `smart_board_preferences` to
  `smartboard_standalone_preferences`.
- SQLite database: `smart-board.db` to `smartboard-standalone.db`.
- ViewModel cancels delayed recognition/auto-shape work before tool changes,
  undo/redo, and explicit recognition to avoid stale mutations.
- Storage Access Framework import/export writes user-selected documents and
  rejects unsupported/corrupt board payloads without replacing the open board.

## Test mapping

All files in
`AIExplorer/app/src/test/java/com/indianservers/aiexplorer/smartboard/`
map by filename to
`SMARTBoard/app/src/test/java/com/indianservers/smartboard/smartboard/`:

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

All files in
`AIExplorer/app/src/androidTest/java/com/indianservers/aiexplorer/smartboard/`
map by filename to the corresponding standalone `androidTest` package:

- `SmartBoardMultiSubjectPhase1UiTest.kt`
- `SmartBoardMultiSubjectPhase3UiTest.kt`
- `SmartBoardPhase1UiTest.kt`

## External implementations migrated

Only the required transitive implementations were copied:

- math/CAS/graph/statistics: 22 files under standalone `core/`, including
  `TrustedMathKernel`, `SymbolicCas`, problem solving, typed graphing and
  `AdvancedStatistics`;
- recognition: `input/CasMultimodalRecognition.kt`;
- formula rendering: `FormulaLatexRenderer.kt`;
- biology: catalogue, models, validator, repository and future-3D metadata;
- chemistry: bundled element data, models and electron configuration engine;
- physics: bundled formula data, models, validator, repository and unit system.

The standalone app has no imports from `com.indianservers.aiexplorer`.

## Contracts and implementations

`services/SmartBoardServices.kt` introduces:

- `SmartBoardCasService`
- `SmartBoardGraphService`
- `SmartBoardStatisticsService`
- `SmartBoardHandwritingService`
- `SmartBoardPhotoRecognitionService`
- `SmartBoardSubjectCatalogue`
- `SmartBoardPhysicsFormulaService`
- `SmartBoardExternalNavigation`

Real local implementations wrap the migrated CAS, graph, statistics,
handwriting/photo recognition, subject catalogues and physics formula data.
The launcher routes Graph 2D/3D, Geometry 2D/3D, and circuit/wave/optics
handoffs to interactive internal tools.

## Resources and Android configuration

- Namespace/application ID: `com.indianservers.smartboard`
- Kotlin/Compose/Material 3, Java 17
- minSdk 31, compile SDK 36.1, target SDK 36
- phone/tablet and optional Leanback/TV installation
- keyboard shortcuts for save, undo, redo, delete and escape
- adaptive compact/wide board UI and custom-canvas pointer/stylus handling
- app-specific SQLite and DataStore names
- Storage Access Framework board import/export and system photo picker
- per-ABI debug and release APKs; no universal APK
- release minification and resource shrinking

Gradle dependencies: Compose BOM/UI/Foundation/Material 3, Activity Compose,
Navigation Compose, lifecycle ViewModel Compose, Preferences DataStore, ML Kit
text recognition, ML Kit Digital Ink, ONNX Runtime Android, JUnit 4, AndroidX
test/Espresso and Compose UI test JUnit4.

## Verification results

- `gradlew testDebugUnitTest assembleDebug`: success.
- Unit tests: 146 tests, 0 failures, 0 errors, 0 skipped.
- `gradlew lintDebug`: success; 0 errors, 68 warnings, 2 hints.
- `gradlew assembleRelease`: success; R8 and resource shrinking enabled.
- `gradlew -PsmartboardBundle=true bundleRelease`: success.
- `gradlew assembleDebugAndroidTest`: success; all migrated UI tests compile.
- AIExplorer `gradlew :app:assembleDebug`: success against the audit baseline.
- AIExplorer `gradlew :app:compileDebugKotlin`: success after the unexpected
  concurrent source-package deletion.
- Android UI tests: all 3 classes compile. Execution on the connected CPH2717
  did not reach Compose because the device was locked
  (`mDreamingLockscreen=true`); 15 tests reported “No compose hierarchies
  found”. This is recorded as pending rather than passed.
- Manual scenarios: pending for the same locked-device reason.

## Package outputs and compressed sizes

- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`: 92,190,101 bytes
  (87.92 MiB)
- `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk`: 77,463,585 bytes
  (73.88 MiB)
- `app/build/outputs/apk/debug/app-x86_64-debug.apk`: 100,599,811 bytes
  (95.94 MiB)
- `app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk`:
  53,279,065 bytes (50.81 MiB)
- `app/build/outputs/apk/release/app-armeabi-v7a-release-unsigned.apk`:
  38,552,549 bytes (36.77 MiB)
- `app/build/outputs/apk/release/app-x86_64-release-unsigned.apk`:
  61,688,775 bytes (58.83 MiB)
- `app/build/outputs/bundle/release/app-release.aab`: 81,864,967 bytes
  (78.07 MiB)

In the arm64 release APK the principal compressed contributors are ONNX
Runtime `libonnxruntime.so` (26.69 MiB), ML Kit OCR native pipeline
(10.55 MiB), ML Kit Digital Ink native runtime (6.57 MiB), and application
DEX (4.94 MiB). Recognition dependencies were retained as required.

## Compatibility and known limitations

- The versioned document codec loads schema versions 0 and 1 and normalizes
  them to the current schema; migration and corrupt/unsupported document
  behavior is covered by unit tests.
- Standalone data is deliberately isolated from AIExplorer. Existing
  AIExplorer private SQLite/DataStore data is not automatically visible to the
  new application; users must export/import a `.smartboard` document.
- Offline image-to-LaTeX requires a compatible user-installed model in
  `filesDir/offline_models/texteller-q4-v2`; absence is surfaced to the user.
- Release APKs are unsigned. The AAB is produced by the configured release
  build but needs production signing configuration for store delivery.
- Device UI/manual verification remains pending; functional completeness is
  therefore not claimed despite successful compilation and unit coverage.
- AIExplorer no longer satisfies the extraction instruction to retain Smart
  Board source: a concurrent external change has already staged those files as
  deletions. This extraction did not restore or otherwise overwrite that
  external work.

## AIExplorer references proposed for later removal

Do not execute this list during extraction Phase 1. At final verification, a
concurrent external change appeared to have already performed items 1 and 2
and much of item 3; the entries remain the exact intended checklist for review.

1. Delete the production package
   `app/src/main/java/com/indianservers/aiexplorer/smartboard/` (37 files).
2. Delete the 16 Smart Board unit tests and 3 Smart Board UI tests in their
   matching package directories.
3. In `app/src/main/java/com/indianservers/aiexplorer/MainActivity.kt`, remove:
   the `SmartBoardFeatureRoot` import; `showSmartBoard` and
   `returnToSmartBoard` snapshot/state/saved-state fields; `openSmartBoard`,
   `openSmartBoardMathModule`, and `returnFromSmartBoardMathModule`; Smart
   Board back-navigation branches; the `SmartBoardFeatureRoot` render block
   and its Graph 2D/3D and Geometry 2D/3D callbacks; and all remaining
   `showSmartBoard` visibility predicates/status strings.
4. Remove Smart Board entry assertions/navigation from
   `app/src/androidTest/java/com/indianservers/aiexplorer/AIExplorerUiTest.kt`.
5. Remove Smart Board launch copy from
   `app/src/main/java/com/indianservers/aiexplorer/ui/home/AiExplorerSplashScreen.kt`
   and matching strings from `app/src/main/res/values/strings.xml` after
   confirming those strings have no other consumer.
6. Re-run repository-wide reference search, AIExplorer unit/UI tests, lint,
   debug build and release build before committing deletion.

## Dependencies AIExplorer may be able to remove later

After the above deletion, run dependency-usage analysis before changing
Gradle. ML Kit Digital Ink, ML Kit text recognition, ONNX Runtime, DataStore
and lifecycle ViewModel Compose are removal candidates only if no remaining
AIExplorer feature imports them. Compose, Navigation Compose, Material 3 and
the shared math/biology/chemistry/physics source engines are used elsewhere
and must not be removed solely because Smart Board is deleted.
