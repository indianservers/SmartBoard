# SMART Board Recognition Audit

The audit implementation is debug-only:

- `app/src/debug/.../audit` contains the dataset, scoring, evidence exporter and manual screen.
- `app/src/androidTest/.../audit` contains automated/hybrid stroke replay against production APIs.
- No audit class is compiled into release builds.

## Modes

### Mode A — automated replay

Build, install, and execute the complete corpus:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-x86_64-debug.apk
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -r `
  -e class com.indianservers.smartboard.smartboard.audit.SmartBoardComprehensiveAuditTest#executeGeneratedAuditAgainstProductionRecognitionPipeline `
  -e auditRunId comprehensive-560 `
  com.indianservers.smartboard.test/androidx.test.runner.AndroidJUnitRunner
```

Optional instrumentation arguments:

| Argument | Example | Meaning |
|---|---|---|
| `auditCategory` | `FRACTIONS_RATIONAL` | Run one category. |
| `caseStart` | `11` | One-based start within the selected corpus. |
| `caseLimit` | `10` | Maximum cases to execute. |
| `auditRunId` | `fraction-regression-01` | Append-only evidence directory. |
| `installFormulaModel` | `true` | Install the offline formula model if absent. |

### Mode B — manual handwriting

The activity exists only in the debug manifest and can be launched through a debug shell:

```powershell
adb shell am start -n `
  com.indianservers.smartboard/.smartboard.audit.SmartBoardAuditActivity
```

The tester writes naturally, runs the same multimodal recognizer, sees raw and normalized output,
records pass/partial/fail and notes, then moves to the next prompt. Hide the expected prompt for a
blind run.

### Mode C — hybrid

Automated cases rotate through 18 stroke-level handwriting profiles, 11 canvas regions and controlled
stroke variants. Variations alter point geometry, pressure, width, timing, order, baseline, scale,
slant and overlap. They are not image rotations.

## Evidence

Device output is stored under:

```text
/sdcard/Android/data/com.indianservers.smartboard/files/audit-output/<run-id>/
```

Pull a completed run:

```powershell
adb pull `
  /sdcard/Android/data/com.indianservers.smartboard/files/audit-output/<run-id> `
  audit-output/<run-id>
```

Each run contains timestamp-isolated stroke JSON, rendered inputs, failure composites, CSV results,
JSON summary, Markdown report and evidence-based recommendations.

## Comparison policy

The raw provider output is saved before normalization. Normalization is transparent and
comparison-only. Literal, semantic, mathematical-structure and spatial-layout outcomes are separate;
semantic equivalence never hides a failed fraction, root, matrix, graph or diagram layout.
