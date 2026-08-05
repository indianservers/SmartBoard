# Recognition Ensemble Architecture and Integration Gate

Date: 2026-08-05

Implementation status:

- Existing-pipeline audit: **IMPLEMENTED**
- Provider-neutral contracts: **IMPLEMENTED**
- Production TexTeller wrapper migration: **IMPLEMENTED, SHADOW ONLY**
- PosFormer specialist: **IMPLEMENTED, EDUCATION-ONLY SHADOW PROVIDER**
- Production routing or scoring changes: **NOT IMPLEMENTED**

## 2026-08-05 education-only implementation update

This update supersedes the earlier PosFormer licensing/conversion planning notes later in this
document. The product owner confirmed that this deployment is for education only. PosFormer
therefore remains restricted to education and academic research, and that restriction is exposed
in model-pack status, provider warnings and export metadata. It must not be enabled in a commercial
distribution without separate permission.

Implemented:

- `TexTellerRecognitionProvider`: long-lived serialized encoder/decoder sessions, warm-up/release,
  cancellation/deadline checks, exact raw output, normalized output and stage timing.
- `PosFormerRecognitionProvider`: optional integrity-checked academic model pack and ONNX Runtime
  Android execution.
- `tools/posformer/export_posformer_onnx.py`: reproducible export from the official epoch-206
  checkpoint into separate encoder and autoregressive decoder graphs.
- ONNX-safe eval-mode masked BatchNorm replacement, equivalent on valid pixels.
- Exact model sizes and SHA-256 hashes recorded in generated metadata.
- Emulator smoke test using separate human-style canvas strokes for `x^{2}+1`.

Measured PosFormer artifacts:

| Artifact | Size |
|---|---:|
| Official PyTorch checkpoint | 78,508,976 bytes |
| FP32 ONNX encoder | 12,671,986 bytes |
| FP32 ONNX decoder | 13,674,143 bytes |
| Vocabulary | 478 bytes |
| Android specialist pack total | 26,346,607 bytes |

Both ONNX graphs pass ONNX checker and desktop ONNX Runtime execution. On Android emulator
`emulator-5554`, the stroke-drawn smoke case produced:

| Expected | Detected | Confidence | Provider time | Assessment |
|---|---|---:|---:|---|
| `x^{2}+1` | `x ^ { 2 } \neq q` | 0.890 | 569 ms | Superscript correct; trailing `+1` incorrect |

The initial test exposed an input-polarity mismatch: official CROHME images use black backgrounds
with white ink, while SMART Board renders black ink on white. Before correction, the same input
produced an unrelated limit expression at 0.333 confidence in 2,366 ms. PosFormer preprocessing now
inverts board luminance to the official tensor convention.

Current safety boundary:

- PosFormer and the new TexTeller provider remain outside production routing.
- All ensemble feature flags still default off.
- Existing recognition output, documents, graph routing and geometry routing are unchanged.
- PosFormer currently uses deterministic left-to-right greedy decoding. Official bidirectional
  beam-10 parity plus a 200-fixture PyTorch-vs-ONNX comparison are required before its hypothesis
  may influence the primary result.

## Baseline evidence reviewed

The complete 560-case report, recommendations, execution notes, summary and all six result CSVs
under `audit-output/comprehensive-560-20260805-final/` were reviewed before adding contracts.

| Baseline | Result |
|---|---:|
| Cases processed | 560 |
| Recognition executed | 549 |
| Passed | 214 |
| Pass rate | 38.2% |
| Exact match | 34.8% |
| Semantic match | 38.9% |
| Timeouts in final accuracy run | 30 |
| Crashes | 0 |
| Valid median latency | 3,338 ms |
| Valid P95 latency | 9,941 ms |
| Valid P99 latency | 28,810 ms |
| Valid maximum latency | 31,476 ms |

The evidence supports staged routing: easy expressions should retain the current fast path, while
scripts, matrices, radicals, multiline expressions, set/logic notation and probability notation
need structural specialization. Graph and geometry ink must remain on their own engines.

## Current production flow

```text
SmartBoardCanvasView
  -> StrokeElement / StrokePoint
  -> SmartBoardViewModel.recognizeSelection()
  -> MathRecognitionRequestBuilder + SHA-256 stroke fingerprint
  -> MathRecognitionInputRenderer (high-contrast PNG, max 2048 px)
  -> subject detection/orchestration
  -> ML Kit digital-ink recognition
  -> optional raster enhancement
       -> TexTeller-Q4-v2 when installed
       -> ML Kit image text fallback otherwise
  -> MultimodalMathRecognitionEngine candidate lattice
  -> StructureAwareRecognitionEnhancer
  -> contextual candidate reranking
  -> SmartBoardSemanticExpressionBuilder
  -> recognition review
  -> user-confirmed insertion as MathExpressionElement
```

Raw strokes remain in the document. Recognition creates a review result and does not silently
replace handwriting.

## TexTeller implementation details

### Model loading and runtime

- Model pack: private app storage at `offline_models/texteller-q4-v2`.
- Runtime: ONNX Runtime Android, already present in the application.
- Encoder: `encoder_model_q4.onnx`, 56,848,921 bytes.
- Decoder: `decoder_model_q4.onnx`, 198,619,422 bytes.
- Vocabulary: 146,663 bytes.
- Total model pack: 255,615,006 bytes.
- Quantization: repository-provided Q4 ONNX encoder and decoder.
- Integrity: fixed expected byte counts and SHA-256 checksums.
- Installation: resumable HTTP range download, partial file, checksum validation, atomic rename.
- Offline operation: complete after initial explicit installation.

`TexTellerOnnxRuntime` currently creates encoder and decoder sessions for every recognition request
and closes them after the request. It does not leak the sessions, but it also does not satisfy the
desired “load once and warm” behavior. A future provider wrapper should own one serialized,
thread-safe session lifecycle with explicit warm-up and memory-pressure release.

### Preprocessing

- Decode the PNG to a bitmap.
- Find non-white content using luminance/alpha thresholds.
- Add proportional padding.
- Aspect-fit and center into a 448 × 448 white image.
- Convert to one grayscale channel.
- Normalize luminance from `[0,1]` to `[-1,1]`.
- Tensor shape: `[1, 1, 448, 448]`.

No preprocessing change is proposed at this stage.

### Decoding and confidence

- Greedy autoregressive decoder.
- Maximum 96 generated tokens.
- GPT-2-style byte vocabulary decoding.
- Control/private/unassigned characters are removed.
- Display wrappers and redundant outer groups are normalized downstream.
- Confidence is the exponentiated mean token log-probability, clamped to `0.35..0.995`.
- TexTeller currently returns one full-expression candidate; alternatives primarily come from the
  digital-ink/image fusion lattice.

The exact TexTeller output is not represented separately from its first normalization step today.
The new provider result contract therefore has distinct `rawOutput` and `normalizedOutput` fields.

## Existing ensemble and reusable components

The system is not single-model:

- `MultimodalMathRecognitionEngine` runs digital ink and raster providers.
- Dedicated formula vision receives more weight than generic handwriting when TexTeller is ready.
- `RecognitionLatticeCandidate` preserves candidate sources and parser verification.
- Parser-valid candidates receive additional score.
- Previous stable output can contribute stability evidence.
- `StructureAwareRecognitionEnhancer` uses stroke geometry for vertical scripts, fraction layout,
  radicals and absolute-value bars.
- `SmartBoardContextualRecognitionReranker` adds nearby/semantic context evidence.
- `SmartBoardCanvasIntelligenceEngine` already provides stroke intent, uncertainty and ranked
  object hypotheses.
- `SmartBoardSemanticExpressionBuilder` already creates a renderer-independent semantic tree.
- `SmartBoardMathGraphIntelligenceEngine` and `DeterministicAutoShapeRecognizer` remain separate
  graph and geometry foundations.

These must be adapted, not duplicated.

## Timeout, cancellation and thread ownership

- Recognition jobs are owned by `SmartBoardViewModel.viewModelScope`.
- Raster rendering and TexTeller inference use background dispatchers.
- ONNX uses one inter-op thread and up to four intra-op threads.
- `MultimodalMathRecognitionEngine` currently allows 60 seconds for digital ink and 30 seconds for
  raster recognition.
- The audit harness applied a separate 10-second request timeout.
- `withTimeoutOrNull` invalidates the coroutine result but cannot reliably interrupt blocking native
  ONNX inference.
- Digital-ink recognizers close on coroutine cancellation.
- A 60-second cooldown suppresses temporarily unavailable digital providers.

The new `RecognitionRequestGate` makes cancellation an optimization and fingerprint/generation
matching the correctness boundary. A late native result cannot be accepted after the same request
ID starts a newer fingerprint or is cancelled.

## Missing abstractions

Before this change there was no common contract that preserved all of:

- Provider identity, version and capabilities
- Raw versus normalized provider output
- Token candidates and token confidence
- Provider structural tree
- Token/stroke/bounding-box associations
- Preprocessing, inference and decoding timing
- Timeout and cancellation terminal state
- Device-cost declaration
- Request fingerprint acceptance
- Warm-up, cancellation and release lifecycle

`recognition/ensemble/MathRecognitionProvider.kt` now defines these contracts. It is deliberately
unwired and all ensemble feature flags default off.

## Specialist model integration plan

### PosFormer assessment

PosFormer is technically well aligned because its position-forest objective explicitly models
hierarchical symbol relationships and the official project reports support for single-line,
multi-line and nested expressions. The official repository includes a 74.9 MB PyTorch checkpoint
and describes a DenseNet/Transformer model.

Sources:

- Official repository: https://github.com/SJTU-DeepVisionLab/PosFormer
- Official paper: https://arxiv.org/abs/2407.07764

However, the repository states that the code is free only for academic research, despite mentioning
the 2-clause BSD licence. That additional restriction is not suitable for an ordinary distributed
product. There is also no standalone licence file in the repository at the time of review.

Decision: **do not bundle or download the PosFormer checkpoint in production** until one of these is
completed:

1. Obtain explicit commercial/product permission covering code, checkpoint and training data.
2. Retrain an equivalent architecture using data and code with verified product-compatible terms.
3. Select another specialist whose code, checkpoint and training-data rights are all compatible.

NAMER and TAMER remain research candidates, but no checkpoint/code licence was sufficiently
verified during this phase to select either one.

### Conversion gate

If licensing is resolved, perform conversion outside the Android app:

1. Freeze preprocessing, vocabulary, position decoder and beam behavior.
2. Export encoder and decoder/position heads to ONNX with fixed reference fixtures.
3. Compare PyTorch and ONNX logits/tokens on at least 200 saved complex audit images.
4. Quantize dynamically or to INT8 only after FP32 parity is established.
5. Reject conversion if exact token parity or structural output materially regresses.
6. Use the existing ONNX Runtime Android dependency; do not introduce PyTorch Android.
7. Package the model as an optional, checksum-verified model pack.

### Size estimate

These are planning estimates, not measured Android artifacts:

| Artifact | Estimated size |
|---|---:|
| Official PyTorch checkpoint | 74.9 MB measured by official repository |
| FP32 ONNX | approximately 70–100 MB |
| INT8/dynamic-quantized ONNX | approximately 20–40 MB |
| Additional vocabulary/metadata | below 2 MB |

Actual encoder/decoder size, operator support and working memory must be measured after export.

## Memory risks

- The existing TexTeller model pack already occupies approximately 255.6 MB.
- The audit observed process PSS grow from approximately 76 MB to 173 MB.
- Loading TexTeller plus a second full-expression model simultaneously may exceed practical
  low-resource device limits.
- Autoregressive or beam decoders allocate growing token tensors.
- Parallel ONNX sessions can multiply activation and native arena memory.
- Recreating TexTeller sessions per request adds cold-start and native-allocation churn.

Required safeguards:

- Lazy specialist loading
- One owned session per provider
- Serialized inference unless measured safe
- Device-tier routing
- Storage and memory checks
- Explicit release under memory pressure
- 100-run PSS/native-memory soak test
- No specialist on the simple-expression route

## Latency risks

The existing median is already 3.3 seconds and P95 is close to 10 seconds. Running two full models
for every expression would violate the interaction target.

Initial orchestration budget:

| Stage | Budget |
|---|---:|
| Preprocessing | 500 ms |
| Primary provider | 4,000 ms |
| Specialist provider | 5,000 ms |
| Consensus/validation | 500 ms |
| Interactive result | 7,000 ms |
| Absolute acceptance ceiling | 10,000 ms |

The orchestrator should return the best valid primary result at the interactive deadline and reject
all late fingerprints. A specialist may start only when geometry or primary output demonstrates
complexity.

## Staged implementation plan

### Stage A — contracts and lifecycle

Status: **IMPLEMENTED**

- Provider-neutral input, capabilities, context and result
- Raw-output preservation
- Token/structure/bounding-box evidence contracts
- Per-stage timing
- Request generation/fingerprint gate
- Default-off ensemble flags
- Contract unit tests

### Stage B — backward-compatible TexTeller provider

Status: **NOT IMPLEMENTED**

- Wrap current TexTeller without changing preprocessing/decoding
- Add timing and raw-output capture
- Reuse one serialized session
- Add warm-up/release
- Apply generation/fingerprint gate
- Shadow-run against the old adapter and require identical output

### Stage C — complexity and routing

Status: **NOT IMPLEMENTED**

- Geometry-based complexity profile
- Graph/geometry early routes
- Simple TexTeller-only route
- Structured/matrix/multiline routes behind flags
- Configurable device budgets

### Stage D — licensed specialist and symbol reranker

Status: **BLOCKED**

- Complete licence gate
- Export/quantization parity
- Optional model pack
- Saved-confusion symbol classifier
- No automatic token replacement without sufficient margin

### Stage E — consensus and audit comparison

Status: **NOT IMPLEMENTED**

- Central configurable consensus weights
- Syntax/spatial/stroke-coverage penalties
- Extend all 560 result rows with provider evidence
- Highlight every previously passing result that changes
- Run targeted error suites and 100-run memory soak

## Regression boundary

This phase does not instantiate the new API from production code. Therefore:

- Existing recognition output is unchanged.
- TexTeller preprocessing and normalization are unchanged.
- Graph and geometry routing is unchanged.
- Saved documents are unchanged.
- APK/model-pack size is unchanged.
- All ensemble flags are off by default.
