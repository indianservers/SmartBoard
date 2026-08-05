param(
    [Parameter(Mandatory = $true)]
    [string]$AccuracyRun,
    [Parameter(Mandatory = $true)]
    [string]$PerformanceRun
)

$ErrorActionPreference = "Stop"
$accuracyRoot = (Resolve-Path -LiteralPath $AccuracyRun).Path
$performanceRoot = (Resolve-Path -LiteralPath $PerformanceRun).Path
$resultsPath = Join-Path $accuracyRoot "results\SMART_BOARD_AUDIT_RESULTS.csv"
$reportPath = Join-Path $accuracyRoot "report\SMART_BOARD_AUDIT_REPORT.md"
$summaryPath = Join-Path $accuracyRoot "report\SMART_BOARD_AUDIT_SUMMARY.json"
$performancePath = Join-Path $accuracyRoot "results\SMART_BOARD_PERFORMANCE_METRICS.csv"
$sourcePerformancePath = Join-Path $performanceRoot "results\SMART_BOARD_PERFORMANCE_METRICS.csv"

$rows = Import-Csv -LiteralPath $resultsPath
$manual = @($rows | Where-Object status -eq "MANUAL_REVIEW_REQUIRED").Count
$recognitionExecuted = $rows.Count - $manual
$passed = @($rows | Where-Object { $_.status -in @("PASS", "PASS_WITH_NORMALIZATION") }).Count
$timeouts = @($rows | Where-Object status -eq "TIMEOUT").Count
$crashes = @($rows | Where-Object status -eq "CRASH").Count

Copy-Item -LiteralPath $performancePath -Destination (Join-Path $accuracyRoot "results\SMART_BOARD_PERFORMANCE_METRICS_SUSPENDED_RUN.csv") -Force
Copy-Item -LiteralPath $sourcePerformancePath -Destination $performancePath -Force
$performance = Import-Csv -LiteralPath $sourcePerformancePath | Where-Object scope -eq "overall" | Select-Object -First 1

function Format-Example([object]$row) {
    $errors = $row.error_types
    return ('- **{0}** - Expected `{1}`; detected `{2}`; normalized `{3}`; confidence `{4}`; status **{5}**; errors `{6}`; evidence `{7}`.' -f
        $row.case_id, $row.expected_plain_text, $row.raw_detected_output,
        $row.normalized_detected_output, $row.confidence, $row.status, $errors, $row.evidence_path)
}

$correct = @($rows | Where-Object { $_.status -in @("PASS", "PASS_WITH_NORMALIZATION") } | Select-Object -First 20)
$partial = @($rows | Where-Object status -eq "PARTIAL" | Select-Object -First 30)
$failed = @($rows | Where-Object { $_.status -notin @("PASS", "PASS_WITH_NORMALIZATION", "PARTIAL") } | Select-Object -First 50)
$examples = @(
    "## 5. Expected versus detected examples",
    "",
    "### 20 correct examples",
    ""
) + @($correct | ForEach-Object { Format-Example $_ }) + @(
    "",
    "### 30 partial examples",
    ""
) + @($partial | ForEach-Object { Format-Example $_ }) + @(
    "",
    "### 50 important failures",
    ""
) + @($failed | ForEach-Object { Format-Example $_ }) + @("", "## 6. Symbol confusion analysis")

$report = Get-Content -LiteralPath $reportPath -Raw
$report = [regex]::Replace(
    $report,
    "(?s)## 5\. Expected versus detected examples.*?## 6\. Symbol confusion analysis",
    ($examples -join [Environment]::NewLine)
)
$performanceSection = @"
## 9. Performance results

Accuracy and status metrics come from the conservative final run. That run was externally suspended
for about one hour, so its wall-clock total and one latency sample are not used for performance.
Performance comes from the earlier uninterrupted run over the identical 560 stroke cases:

| Metric | Result |
|---|---:|
| Median | $($performance.median_ms) ms |
| P90 | $($performance.p90_ms) ms |
| P95 | $($performance.p95_ms) ms |
| P99 | $($performance.p99_ms) ms |
| Mean | $([math]::Round([double]$performance.mean_ms, 1)) ms |
| Maximum | $($performance.max_ms) ms |
| Timeouts | $($performance.timeouts) |
| Crashes | $($performance.crashes) |

The production provider performs blocking inference, so coroutine cancellation did not always stop
work at the requested 10-second deadline; P99 and maximum latency therefore exceed the target.

## 10. Root-cause analysis
"@
$report = [regex]::Replace(
    $report,
    "(?s)## 9\. Performance results.*?## 10\. Root-cause analysis",
    $performanceSection
)
$runNote = @"
> **Run provenance:** Accuracy/status results use the conservative final scoring run. It processed
> $($rows.Count) cases, executed recognition for $recognitionExecuted, and marked $manual cases
> `MANUAL_REVIEW_REQUIRED` because the automated glyph writer could not represent them faithfully.
> Valid performance timing comes from the earlier uninterrupted identical-corpus run. The final
> accuracy run had an external emulator suspension and is not used for latency.

"@
$report = $report.Replace("## 1. Executive summary", "## 1. Executive summary`r`n`r`n$runNote")
Set-Content -LiteralPath $reportPath -Value $report -Encoding UTF8

$summary = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
$summary | Add-Member -NotePropertyName recognition_executed_cases -NotePropertyValue $recognitionExecuted -Force
$summary | Add-Member -NotePropertyName manual_review_required_cases -NotePropertyValue $manual -Force
$summary | Add-Member -NotePropertyName accuracy_run_suspended_externally -NotePropertyValue $true -Force
$summary | Add-Member -NotePropertyName performance_run -NotePropertyValue (Split-Path $performanceRoot -Leaf) -Force
$summary | Add-Member -NotePropertyName performance_median_ms -NotePropertyValue ([long]$performance.median_ms) -Force
$summary | Add-Member -NotePropertyName performance_p95_ms -NotePropertyValue ([long]$performance.p95_ms) -Force
$summary | Add-Member -NotePropertyName performance_p99_ms -NotePropertyValue ([long]$performance.p99_ms) -Force
$summary | Add-Member -NotePropertyName performance_max_ms -NotePropertyValue ([long]$performance.max_ms) -Force
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$notes = @"
# Audit Execution Notes

| State | Count |
|---|---:|
| GENERATED | 560 |
| COMPILED | 560-case harness and debug manual screen |
| PROCESSED BY FINAL RUN | $($rows.Count) |
| RECOGNITION EXECUTED | $recognitionExecuted |
| MANUAL_REVIEW_REQUIRED | $manual |
| PASSED | $passed |
| TIMEOUT | $timeouts |
| CRASH | $crashes |
| MANUALLY VERIFIED | 0 |

The final scoring run was externally suspended for about one hour. It remains valid for recognition
outputs and accuracy classifications, but not for performance timing. Required performance metrics
were copied from the earlier uninterrupted run over the identical corpus. The suspended-run metrics
are retained as `SMART_BOARD_PERFORMANCE_METRICS_SUSPENDED_RUN.csv`.
"@
Set-Content -LiteralPath (Join-Path $accuracyRoot "report\AUDIT_EXECUTION_NOTES.md") -Value $notes -Encoding UTF8
