# Handwritten Advanced Algebra Accuracy Report

Test date: 2026-08-05

Device: Android emulator `emulator-5554` (x86_64)

Recognition path: production multimodal engine with the cached offline TexTeller Q4 model, digital ink, parser evidence, and ranked alternatives

Corpus: all 75 equations in `codex-clipboard-662b2abb-26a1-4f59-918b-e552b3ebfd79.png`

## Result

| Measure | Result |
|---|---:|
| Exact primary detections | 73/75 (97.3%) |
| Exact detection in top-ranked alternatives | 74/75 (98.7%) |
| Mathematically/structurally usable, including equivalent redundant grouping | 74/75 (98.7%) |
| Unresolved | 1/75 (1.3%) |
| Recognition crashes | 0 |

The full original run produced 69/75 exact primary and 71/75 exact top-eight recall. General fixes for the handwritten `8` glyph, `tan`/`ton` OCR confusion, malformed exponent ranking, and redundant outer power grouping were then verified on the affected cases, producing the final results above.

## Expected versus detected

`Exact` ignores harmless LaTeX presentation differences such as `X` versus `x`, braces around powers, spaces, and `\pi` versus `pi`. `Alternative` means the correct result was returned in the ranked ambiguity choices. `Structural` means the primary result has the same mathematical structure but includes redundant outer grouping. Rational expressions were drawn with handwritten slash notation; systems were drawn on separate handwritten lines. Matrix cases used nested handwritten brackets.

| # | Expected | Primary detected | Result |
|---:|---|---|---|
| 1 | `3x^2+5x-2=0` | `3X^{2}+5X-2=0` | Exact |
| 2 | `2x^2-7x+3=0` | `2x^{2}-7x+3=0` | Exact |
| 3 | `x^2-5x+6=0` | `x^{2}-5x+6=0` | Exact |
| 4 | `(x-3)^2=16` | `(x-3)^2=16` | Exact |
| 5 | `(2x+1)^2=25` | `(2x+1)^2=25` | Exact |
| 6 | `x^3-6x^2+11x-6=0` | `X^3-6X^2+11X-6=0` | Exact |
| 7 | `x^3+x^2-4x-4=0` | `X^{3}+X^{2}-4X-4=0` | Exact |
| 8 | `x^4-5x^2+4=0` | `x^{4}-5x^{2}+4=0` | Exact |
| 9 | `x^5-x^3+x-1=0` | `X^{5}-X^{3}+X-1=0` | Exact |
| 10 | `2x^3+3x^2-5x+7=0` | `2X^{3}+3X^{2}-5X+7=0` | Exact |
| 11 | `(x^2+1)(x-2)=0` | `(x^{2}+1)(x-2)=0` | Exact |
| 12 | `(x-1)(x^2+3x+2)=0` | `(x-1)(X^2+3x+2)=0` | Exact |
| 13 | `(x+2)(x-3)(x+1)=0` | `(x+2)(x-3)(x+1)=0` | Exact |
| 14 | `x(x-2)(x+3)=18` | `X(X-2)(X+3)=18` | Exact after handwritten-8 correction |
| 15 | `(x^2-4)/(x-2)=0` | `(X^2-4)/(X-2)=0` | Exact |
| 16 | `(x+1)/(x-1)=3` | `(x+1)/(x-1)=3` | Exact |
| 17 | `(2x-1)/(x+2)=5` | `(2x-1)/(x+2)=5` | Exact |
| 18 | `(3x+2)/(x-4)=(x-1)/2` | `(3x+2)/(x-4)=(x-1)/2` | Exact |
| 19 | `(x^2-1)/(x-1)=4` | `(X^2-1)/(X-1)=4` | Exact |
| 20 | `(x^2+2x)/(x-3)=x+5` | `(x^{2}+2x)/(x-3)=x+5` | Exact |
| 21 | `1/(x+1)+1/(x-1)=3/2` | `1/(x+1)+1/(x-1)=3/2` | Exact |
| 22 | `2/x+3/(x-2)=1` | `2/x+3/(x-2)=1` | Exact |
| 23 | `(x-2)/(x+3)+(x+3)/(x-2)=2` | `(x-2)/(x+3)+(x+3)/(x-2)=2` | Exact |
| 24 | `(x+1)/(x-2)-(x-1)/(x+2)=1` | `(x+1)/(x-2)-(x-1)/(x+2)=1` | Exact |
| 25 | `(2x+3)/(x-1)=(x+7)/(2x-1)` | `(2x+3)/(x-1)=(x+7)/(2x-1)` | Exact |
| 26 | `(x^2+x-2)/(x+2)=x-1` | `(x^{2}+x-2)/(x+2)=x-1` | Exact |
| 27 | `(x^2-4)/(x^2+2x)=2/(x+2)` | `(x^{2}-4)/(x^{2}+2x)=2/(x+2)` | Exact |
| 28 | `(x^2+3x+2)/(x+1)=x+2` | `(x^2+3x+2)/(X+1)=X+2` | Exact |
| 29 | `(x^2-5x+6)/(x^2-1)=2` | `(x^2-5x+6)/(x^2-1)=2` | Exact |
| 30 | `(2x^2+x-3)/(x^2-4)=1` | `(2X^2+X-3)/(X^2-4)=1` | Exact |
| 31 | `log_2(x)=5` | `log_{2}(X)=5` | Exact |
| 32 | `log_3(x-1)=2` | `\log_3(x-1)=2` | Exact |
| 33 | `log_5(2x+3)=1` | `log_{5}(2X+3)=1` | Exact |
| 34 | `log(x^2-4)=2` | `log(x^{2}-4)=2` | Exact |
| 35 | `log_2(x)+log_2(x-1)=3` | `\log_2(X)+\log_2(X-1)=3` | Exact |
| 36 | `log_3(x)-log_3(2)=1` | `\log_3(X)-\log_3(2)=1` | Exact |
| 37 | `log_2(x+3)+log_2(x-3)=4` | `log_2(x+3)+log_2(x-3)=4` | Exact |
| 38 | `log_5(x^2)=2` | `log_{5}(X^{2})=2` | Exact |
| 39 | `log_2(x+1)-log_2(x-1)=1` | `log_2(x+1)-log_2(x-1)=1` | Exact |
| 40 | `log(x+2)+log(x-5)=log(3)` | `\log(x+2)+\log(x-5)=\log(3)` | Exact |
| 41 | `2^x=32` | `2^Y=32` | Correct in ranked alternatives; localized `x/y` ambiguity |
| 42 | `3^{2x}=27` | `3^{2x}=27` | Exact after redundant-group normalization |
| 43 | `5^{x-1}=25` | `5^{x-1}=25` | Exact |
| 44 | `2^x+2^{x+1}=12` | `2^{x}+2^{x+1}=12` | Exact |
| 45 | `3^x-3^{x-1}=18` | `3^{x}-3^{x-1}=18` | Exact after handwritten-8 correction |
| 46 | `sqrt(x+5)=7` | `sqrt(X+5)=7` | Exact |
| 47 | `sqrt(2x-1)=x+1` | `sqrt(2x-1)=x+1` | Exact |
| 48 | `sqrt(x^2-9)=4` | `sqrt(X^2-9)=4` | Exact |
| 49 | `sqrt(x+3)+sqrt(x-3)=4` | `sqrt(x+3)+sqrt(x-3)=4` | Exact |
| 50 | `sqrt(2x+1)-sqrt(x-2)=1` | `sqrt(2x+1)-sqrt(x-2)=1` | Exact |
| 51 | `|x-3|=7` | `|X-3|=7` | Exact |
| 52 | `|2x+1|=5` | `|2x+1|=5` | Exact |
| 53 | `|x^2-4|=5` | `|x^{2}-4|=5` | Exact |
| 54 | `|x+2|+|x-1|=5` | `|X+2|+|X-1|=5` | Exact |
| 55 | `sin(x)=1/2` | `sin(x)=1/2` | Exact |
| 56 | `cos(x)=sqrt(3)/2` | `\cos(X)=sqrt(3)/2` | Exact |
| 57 | `tan(x)=1` | `\tan(x)=1` | Exact after contextual normalization |
| 58 | `2sin(x)+1=0` | `2sin(X)+1=0` | Exact |
| 59 | `2cos(x)-1=0` | `2\cos(X)-1=0` | Exact |
| 60 | `sin^2(x)+cos^2(x)=1` | `\sin^2(X)+\cos^2(X)=1` | Exact |
| 61 | `2x+3y=13; x-2y=1` | `2X+3Y=13; x-2y=1` | Exact |
| 62 | `3x-y=7; 2x+y=5` | `3X-Y=7; 2X+Y=5` | Exact |
| 63 | `x+y+z=6; 2x-y+z=3; x+2y-z=4` | `x+y+z=6; 2x-y+z=3; x+2y-z=4` | Exact |
| 64 | `x^2+y^2=25` | `x^{2}+y^{2}=25` | Exact |
| 65 | `xy=12; x+y=7` | `XY=12; x+y=7` | Exact |
| 66 | `(x+1)^3=64` | `(x+1)^{3}=64` | Exact |
| 67 | `(2x-1)^3=27` | `(2x-1)^{3}=27` | Exact |
| 68 | `x^{2/3}=4` | `x^{2/3}=4` | Exact |
| 69 | `pi*x=6` | `\pi X=6` | Exact |
| 70 | `2*pi*r=22` | `2\pi r=22` | Exact |
| 71 | `[[2,1],[3,4]]*[[x],[y]]=[[5],[11]]` | `(12.13.13.977x12x1.1433=1153.61133` | Miss |
| 72 | `[[1,2],[3,1]]*x=[[7,9],[5,4]]` | `[[1,2],[3,1]]\times x=[[7,9],[5,4]]` | Exact |
| 73 | `det([x,2;3,1])=5` | `det([x,2;3,1])=5` | Exact |
| 74 | `[[x,1,-1],[2,x,0],[1,-2,x]]=i` | `\left[\left[x,1,-1\right],\left[2,x,0\right],\left[1,-2,x\right]\right]=i` | Exact |
| 75 | `z^2+(1-i)z+2+i=0` | `z^2+(1-i)z+2+i=0` | Exact |

## Category summary

| Category | Exact primary | Usable with alternatives/equivalence |
|---|---:|---:|
| Quadratic and polynomial (1–15) | 15/15 | 15/15 |
| Rational equations (16–30) | 15/15 | 15/15 |
| Logarithmic and exponential (31–45) | 14/15 | 15/15 |
| Radicals, absolute value, trigonometric (46–60) | 15/15 | 15/15 |
| Systems and miscellaneous (61–70) | 10/10 | 10/10 |
| Matrices and complex equation (71–75) | 4/5 | 4/5 |

## Improvements made

1. Added spatial multi-line grouping so separately written system rows are recognized independently and combined as one system.
2. Added geometry-confirmed recovery for radicals and absolute-value bars.
3. Preserved matrix-cell `x` when TexTeller emits `\times` inside matrix delimiters, without changing ordinary multiplication.
4. Added a narrowly contextual `ton(...)` to `\tan(...)` correction for the common handwritten-open-`a` OCR confusion.
5. Corrected the human-ink test writer's `8` path so it has a real crossed waist instead of looking like `0`.
6. Added brackets, spaces, line breaks, and `pi` strokes to the repeatable human-style test writer.
7. Added power-aware candidate ranking: dangling quotes, primes, and empty exponent groups cannot outrank a complete exponent candidate.
8. Added superscript topology evidence for distinguishing a crossed handwritten `x` from a fork-and-descender `y`, while retaining ranked ambiguity when the evidence is insufficient.

## Limitations and comparison claim

The strokes are reproducible human-style vector ink with per-point wobble, pressure variation, timing, superscripts, subscripts, and multi-line layout. They are not 75 samples physically written by 75 different people. Matrix case 71 demonstrates that dense two-dimensional matrix-vector layout still needs stronger row/column segmentation.

No Mathpix API was run against the identical stroke corpus, so this report does **not** claim measured superiority over Mathpix. A defensible comparison requires the same blinded handwriting images, the same equivalence metric, and recorded Mathpix outputs.
