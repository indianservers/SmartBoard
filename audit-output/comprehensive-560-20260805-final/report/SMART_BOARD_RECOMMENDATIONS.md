# Smart Board Audit Recommendations

## P2 — Letter confusion

- Problem: `LETTER_CONFUSION` affected 206 executed cases.
- Evidence: basic_arithmetic-036, algebraic_expressions-005, algebraic_expressions-012, algebraic_expressions-014, algebraic_expressions-016, algebraic_expressions-017, algebraic_expressions-018, algebraic_expressions-019, algebraic_expressions-023, algebraic_expressions-025, algebraic_expressions-028, algebraic_expressions-029 and 194 more.
- Likely root cause: symbol classifier ambiguity or insufficient structural evidence.
- Proposed fix: add evidence-gated candidate ranking trained against the saved failing strokes.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from BASIC_ARITHMETIC, ALGEBRAIC_EXPRESSIONS, EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, COMPLEX_NUMBERS, LOG_EXP_SPECIAL, TRIGONOMETRY, CALCULUS, MATRICES_VECTORS, PROBABILITY_STATISTICS, SETS_LOGIC.
- Expected improvement: recover the 206 directly affected cases without changing unrelated categories.

## P2 — Digit confusion

- Problem: `DIGIT_CONFUSION` affected 66 executed cases.
- Evidence: basic_arithmetic-015, basic_arithmetic-023, basic_arithmetic-035, basic_arithmetic-036, algebraic_expressions-007, algebraic_expressions-014, algebraic_expressions-016, algebraic_expressions-019, algebraic_expressions-037, equations_inequalities-018, equations_inequalities-030, powers_subscripts_roots-009 and 54 more.
- Likely root cause: symbol classifier ambiguity or insufficient structural evidence.
- Proposed fix: add evidence-gated candidate ranking trained against the saved failing strokes.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from BASIC_ARITHMETIC, ALGEBRAIC_EXPRESSIONS, EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, COMPLEX_NUMBERS, LOG_EXP_SPECIAL, TRIGONOMETRY, CALCULUS, MATRICES_VECTORS, PROBABILITY_STATISTICS.
- Expected improvement: recover the 66 directly affected cases without changing unrelated categories.

## P2 — Low confidence

- Problem: `LOW_CONFIDENCE` affected 66 executed cases.
- Evidence: algebraic_expressions-007, algebraic_expressions-027, algebraic_expressions-028, algebraic_expressions-035, equations_inequalities-030, equations_inequalities-033, powers_subscripts_roots-018, powers_subscripts_roots-022, powers_subscripts_roots-023, powers_subscripts_roots-034, fractions_rational-018, fractions_rational-022 and 54 more.
- Likely root cause: symbol classifier ambiguity or insufficient structural evidence.
- Proposed fix: add evidence-gated candidate ranking trained against the saved failing strokes.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from ALGEBRAIC_EXPRESSIONS, EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, COMPLEX_NUMBERS, LOG_EXP_SPECIAL, TRIGONOMETRY, CALCULUS, MATRICES_VECTORS, PROBABILITY_STATISTICS, SETS_LOGIC.
- Expected improvement: recover the 66 directly affected cases without changing unrelated categories.

## P0 — Timeout

- Problem: `TIMEOUT` affected 30 executed cases.
- Evidence: equations_inequalities-010, powers_subscripts_roots-004, powers_subscripts_roots-006, powers_subscripts_roots-027, complex_numbers-006, complex_numbers-026, log_exp_special-012, log_exp_special-032, trigonometry-034, matrices_vectors-006, matrices_vectors-023, matrices_vectors-025 and 18 more.
- Likely root cause: the blocking recognition provider continues inference after coroutine cancellation, so the requested deadline is not a hard execution boundary.
- Proposed fix: enforce the deadline at the provider/session boundary, abandon late fusion results by request fingerprint, and add cancellation tests for every saved timeout stroke.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, COMPLEX_NUMBERS, LOG_EXP_SPECIAL, TRIGONOMETRY, MATRICES_VECTORS, PROBABILITY_STATISTICS, SETS_LOGIC.
- Expected improvement: recover the 30 directly affected cases without changing unrelated categories.

## P2 — Graph type wrong

- Problem: `GRAPH_TYPE_WRONG` affected 29 executed cases.
- Evidence: graphs-006, graphs-007, graphs-008, graphs-009, graphs-010, graphs-011, graphs-012, graphs-013, graphs-014, graphs-015, graphs-019, graphs-020 and 17 more.
- Likely root cause: axes/curve separation or fitted-family coverage is incomplete.
- Proposed fix: expand graph-family fitting and require crossing-axis evidence before treating curves as text noise.
- Likely modules: SmartBoardMathGraphIntelligenceEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from GRAPHS.
- Expected improvement: recover the 29 directly affected cases without changing unrelated categories.

## P3 — Subscript missed

- Problem: `SUBSCRIPT_MISSED` affected 26 executed cases.
- Evidence: algebraic_expressions-017, powers_subscripts_roots-010, powers_subscripts_roots-015, powers_subscripts_roots-026, powers_subscripts_roots-029, fractions_rational-029, log_exp_special-004, log_exp_special-015, log_exp_special-016, calculus-007, calculus-008, calculus-010 and 14 more.
- Likely root cause: weak vertical-zone assignment or premature baseline grouping.
- Proposed fix: delay parent assignment until the local vertical neighborhood stabilizes.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from ALGEBRAIC_EXPRESSIONS, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, LOG_EXP_SPECIAL, CALCULUS, MATRICES_VECTORS, PROBABILITY_STATISTICS.
- Expected improvement: recover the 26 directly affected cases without changing unrelated categories.

## P3 — Fraction misread

- Problem: `FRACTION_MISREAD` affected 25 executed cases.
- Evidence: basic_arithmetic-023, equations_inequalities-034, powers_subscripts_roots-020, powers_subscripts_roots-033, fractions_rational-005, fractions_rational-007, fractions_rational-018, fractions_rational-026, fractions_rational-027, fractions_rational-029, trigonometry-018, trigonometry-022 and 13 more.
- Likely root cause: horizontal-bar scope was not associated with numerator and denominator groups.
- Proposed fix: re-run local grouping when a horizontal stroke crosses symbol boxes and preserve bar-first/bar-last alternatives.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from BASIC_ARITHMETIC, EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, TRIGONOMETRY, CALCULUS, PROBABILITY_STATISTICS.
- Expected improvement: recover the 25 directly affected cases without changing unrelated categories.

## P3 — Superscript missed

- Problem: `SUPERSCRIPT_MISSED` affected 20 executed cases.
- Evidence: equations_inequalities-030, powers_subscripts_roots-018, powers_subscripts_roots-029, powers_subscripts_roots-034, powers_subscripts_roots-038, fractions_rational-005, fractions_rational-029, log_exp_special-016, log_exp_special-020, trigonometry-025, trigonometry-029, calculus-010 and 8 more.
- Likely root cause: weak vertical-zone assignment or premature baseline grouping.
- Proposed fix: delay parent assignment until the local vertical neighborhood stabilizes.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, LOG_EXP_SPECIAL, TRIGONOMETRY, CALCULUS, MATRICES_VECTORS.
- Expected improvement: recover the 20 directly affected cases without changing unrelated categories.

## P3 — Root scope error

- Problem: `ROOT_SCOPE_ERROR` affected 16 executed cases.
- Evidence: basic_arithmetic-036, equations_inequalities-006, equations_inequalities-024, powers_subscripts_roots-008, powers_subscripts_roots-009, powers_subscripts_roots-021, powers_subscripts_roots-022, powers_subscripts_roots-023, powers_subscripts_roots-033, fractions_rational-007, fractions_rational-018, fractions_rational-026 and 4 more.
- Likely root cause: symbol classifier ambiguity or insufficient structural evidence.
- Proposed fix: add evidence-gated candidate ranking trained against the saved failing strokes.
- Likely modules: StructureAwareRecognitionEnhancer, MultimodalMathRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from BASIC_ARITHMETIC, EQUATIONS_INEQUALITIES, POWERS_SUBSCRIPTS_ROOTS, FRACTIONS_RATIONAL, COMPLEX_NUMBERS, LOG_EXP_SPECIAL, PROBABILITY_STATISTICS.
- Expected improvement: recover the 16 directly affected cases without changing unrelated categories.

## P1 — Matrix row error

- Problem: `MATRIX_ROW_ERROR` affected 16 executed cases.
- Evidence: matrices_vectors-001, matrices_vectors-002, matrices_vectors-004, matrices_vectors-005, matrices_vectors-007, matrices_vectors-009, matrices_vectors-013, matrices_vectors-017, matrices_vectors-018, matrices_vectors-019, matrices_vectors-020, matrices_vectors-021 and 4 more.
- Likely root cause: dense row/column segmentation and bracket ownership are ambiguous.
- Proposed fix: score bracket-constrained row and column partitions before flattening cells.
- Likely modules: StructureAwareRecognitionEnhancer, SmartBoardSemanticRecognitionEngine.
- Regression risk: medium; preserve raw candidates and gate any new post-processing by structural evidence.
- Tests required: replay the original saved strokes plus adjacent passing cases from MATRICES_VECTORS.
- Expected improvement: recover the 16 directly affected cases without changing unrelated categories.
