# Smart Board AI roadmap

The product is offline-first and classroom-first. Its initial academic scope is Mathematics,
Physics, Chemistry and Biology, with Auto Detect available when a board is not locked to one
subject.

## Phase 1 — reliable classroom recognition

- Keep teacher ink as the source of truth and show an editable recognition review.
- Route recognized work to Mathematics, Physics, Chemistry or Biology.
- Let a teacher lock the board subject so auto-detection cannot override classroom context.
- Support automatic recognition after a writing pause for all four subjects.
- Provide Mathematics Graph mode. A confirmed graphable expression creates a linked graph
  object and immediately opens the existing editable 2D graph workspace.
- Recognize common function notation case-insensitively, including `Sin(45)`.
- Preserve matrix structures as semantic matrix content when the recognizer returns bracketed
  rows such as `[1,2;3,4]`.
- Work locally after the required ML Kit handwriting model has been downloaded once.

Phase 1 acceptance checks:

1. The settings screen offers only Auto Detect plus the four initial subjects.
2. A locked subject wins over a high-confidence automatic subject guess.
3. `Sin(45)` is classified as a mathematical function and can be prepared for graphing.
4. `[1,2;3,4]` is classified and reconstructed as a matrix.
5. Source strokes remain available after recognition and insertion.

## Phase 2 — diagrams and subject tools

- Use stroke geometry and local vision to identify teacher-drawn Physics diagrams, Chemistry
  structures and Biology objects.
- Convert accepted drawings into editable, labeled objects without replacing the original ink.
- Add subject actions such as free-body analysis, reaction balancing and biological labeling.
- Add classroom-specific local vocabularies and correction memory.

## Phase 3 — intelligent teaching workspace

- Reconstruct editable 2D/3D objects and interactive simulations from board content.
- Understand relationships across the whole board and verify multi-step student work.
- Add teacher-controlled tutoring, lesson summaries and export.
- Optimize local models for smart-board hardware; use online intelligence only as an explicit,
  privacy-aware enhancement.

## Device observation

ADB-generated straight-line strokes are not equivalent to human handwriting. On the attached
Oppo CPH2717, the original English digital-ink model read the first highly angular synthetic
`Sin(45)` attempt as `THEY`, and a synthetic bracketed 2×2 matrix as `I4`. The application
correctly kept those low-quality results in the editable review instead of silently replacing
the ink. The semantic pipeline handles a matrix correctly once recognition supplies bracketed
rows, but reliable cell extraction from raw ink needs a dedicated math-ink model or a
geometry-aware matrix recognizer. Human-stylus acceptance data should be collected for a
representative set of teachers before recognition quality is considered production-ready.
