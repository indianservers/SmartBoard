# Handwritten equation recognition accuracy report

Test date: 2026-08-05
Device: Android Emulator `emulator-5554`, API 35
Corpus: 75 pressure-varying, deterministically jittered handwritten equations from the supplied sheet
Engine: digital ink + offline TexTeller Q4 formula vision + parser/geometry fusion

## Summary

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Primary structurally correct | 54/75 (72.00%) | **67/75 (89.33%)** | **+17.33 points** |
| Correct within top 8 | 58/75 (77.33%) | **71/75 (94.67%)** | **+17.34 points** |
| Primary failures | 21 | **8** | **13 fewer** |
| Crash status | No crash | **No crash** | — |

Category results after improvement:

| Category | Primary | Top 8 |
|---|---:|---:|
| Quadratic and polynomial, 1–15 | 13/15 | 14/15 |
| Rational equations, 16–30 | 14/15 | 15/15 |
| Logarithmic and exponential, 31–45 | 12/15 | 12/15 |
| Radicals, absolute values and trigonometry, 46–60 | 14/15 | 15/15 |
| Systems, miscellaneous and π equations, 61–75 | 14/15 | 15/15 |

“Exact” means equivalent mathematical structure after normalizing harmless LaTeX formatting,
letter case, whitespace and explicit multiplication. Symbols, constants, operators, grouping,
powers and equation boundaries must still agree.

## Expected versus detected

| # | Expected | Detected primary | Result |
|---:|---|---|:---:|
| 1 | `2x^2+5x-3=0` | `2X^{2}+5X-3=0` | Exact |
| 2 | `3x^2-7x+2=0` | `3x^{2}-7x+2=0` | Exact |
| 3 | `x^2-9=0` | `x^2-9=0` | Exact |
| 4 | `(x+4)^2=25` | `(x++)^2=25` | Top 8 |
| 5 | `(2x-3)^2=49` | `(2x-3)^{2}=49` | Exact |
| 6 | `x^3-8=0` | `x^3-0=0` | Miss |
| 7 | `x^3+27=0` | `x^{3}+27=0` | Exact |
| 8 | `x^3-3x^2+2x=0` | `X^{3}-3X^{2}+2X=0` | Exact |
| 9 | `x^4-5x^2+4=0` | `x^{4}-5x^{2}+4=0` | Exact |
| 10 | `2x^3+x^2-5x+2=0` | `2X^{3}+X^{2}-5X+2=0` | Exact |
| 11 | `3x^4-4x^2+x-1=0` | `3X^4-4X^2+X-1=0` | Exact |
| 12 | `(x^2+1)(x-3)=0` | `(X^2+1)(X-3)=0` | Exact |
| 13 | `x^5-x^3+x=0` | `X^{5}-X^{3}+X=0` | Exact |
| 14 | `2x^2-3x-2=0` | `2x^{2}-3x-2=0` | Exact |
| 15 | `(x-1)(x^2+x+1)=0` | `(x-1)(x^{2}+x+1)=0` | Exact |
| 16 | `(x-2)/(x+1)=3` | `(X-2)/(X+1)=3` | Exact |
| 17 | `(2x+1)/(x-2)=5` | `(2x+1)/(x-2)=5` | Exact |
| 18 | `(3x-4)/(2x+5)=7` | `(3x-4)/(2x+5)=7` | Exact |
| 19 | `(x^2-1)/(x-1)=6` | `(x^2-1)/(x-1)=6` | Exact |
| 20 | `(x^2+3x)/(x-2)=4` | `(x^2+3X)/(x-2)=4` | Exact |
| 21 | `x/(x-3)+2=5` | `x/(x-3)+2=5` | Exact |
| 22 | `1/(x+2)-3=4/(x+2)` | `1/(x+2)-3=4/(x+2)` | Exact |
| 23 | `2/x+3/(x-1)=1` | `2×x+3×(x-1)=1` | Top 8 |
| 24 | `x/(x+1)=2/(x-3)` | `x/(x+1)=2/(x-3)` | Exact |
| 25 | `(2x+3)/(x-5)=(x+7)/(2x-1)` | `(2x+3)/(x-5)=(x+7)/(2x-1)` | Exact |
| 26 | `(x-1)/(x+2)+(x+2)/(x-1)=5` | `(x-1)/(x+2)+(x+2)/(x-1)=5` | Exact |
| 27 | `(x^2-4)/(x^2+2x)=2/(x+4)` | `(x^2-4)/(x^2+2x)=2/(x+4)` | Exact |
| 28 | `(x^2+2x+1)/(x+1)=3` | `(x^{2}+2x+1)/(x+1)=3` | Exact |
| 29 | `(x^2-5x+6)/(x^2-1)=2` | `(x^2-5x+6)/(x^2-1)=2` | Exact |
| 30 | `(x+3)/(x-3)-(x-3)/(x+3)=2` | `(x+3)/(x-3)-(x-3)/(x+3)=2` | Exact |
| 31 | `log_2(x)=3` | `log_2(X)=3` | Exact |
| 32 | `log_3(x-1)=2` | `log_3(x-1)=2` | Exact |
| 33 | `log_5(2x+3)=1` | `1095(2x+3)=1` | Miss |
| 34 | `log(x^2-4)=2` | `log(x^{2}-4)=2` | Exact |
| 35 | `log_2(x)+log_2(x-1)=3` | `1092(x)+1092(x-13=3` | Miss |
| 36 | `log_3(x)-log_3(2)=1` | `\log_3(X)-\log_3(2)=1` | Exact |
| 37 | `log_2(x+3)+log_2(x-3)=4` | `log_2(x+3)+log_2(x-3)=4` | Exact |
| 38 | `log_5(x^2)=2` | `log_{5}(X^{2})=2` | Exact |
| 39 | `log_2(x+1)-log_2(x-1)=1` | `\log_2(x+1)-\log_2(x-1)=1` | Exact |
| 40 | `log(x+2)+log(x-5)=log(3)` | `\log(x+2)+\log(x-5)=\log(3)` | Exact |
| 41 | `2^{x+1}=16` | `2^{x+1}=16` | Exact |
| 42 | `3^{2x}=27` | `3^{2x}=27` | Exact |
| 43 | `5^{x-1}=25` | `5^{x-1}=25` | Exact |
| 44 | `2^x+2^{x+1}=12` | `2^{x}+2^{x+1}=12` | Exact |
| 45 | `3^x-3^{x-1}=18` | `3^{x}-3^{x-1}=10` | Miss |
| 46 | `sqrt(x+5)=7` | `sqrt(x+5)=7` | Exact |
| 47 | `sqrt(2x-1)=x+1` | `sqrt(2x-1)=x+1` | Exact |
| 48 | `sqrt(x^2-9)=4` | `sqrt(X^2-9)=4` | Exact |
| 49 | `sqrt(x+3)+sqrt(x-3)=4` | `sqrt(x+3)+sqrt(x-3)=4` | Exact |
| 50 | `sqrt(2x+1)-sqrt(x-2)=1` | `sqrt(2x+1)-sqrt(x-2)=1` | Exact |
| 51 | `|x-3|=7` | `|X-3|=7` | Exact |
| 52 | `|2x+1|=5` | `|2x+1|=5` | Exact |
| 53 | `|x^2-4|=5` | `|x^{2}-4|=5` | Exact |
| 54 | `|x+2|+|x-1|=5` | `|X+2|+|X-1|=5` | Exact |
| 55 | `sin(x)=1/2` | `sin(x)=1/2` | Exact |
| 56 | `cos(x)=sqrt(3)/2` | `\cos(x)=sqrt(3)/2` | Exact |
| 57 | `tan(x)=1` | `ton(X)=1` | Top 8 |
| 58 | `2sin(x)+1=0` | `2sin(X)+1=0` | Exact |
| 59 | `2cos(x)-1=0` | `2\cos(X)-1=0` | Exact |
| 60 | `sin^2(x)+cos^2(x)=1` | `\sin^2(X)+\cos^2(X)=1` | Exact |
| 61 | `2x+3y=13; x-2y=1` | `2X+3Y=13; X-2Y=1` | Exact |
| 62 | `3x-y=7; 2x+y=5` | `3x-y=7; 2X+Y=5` | Exact |
| 63 | `x+y+z=6; 2x-y+z=3; x+2y-z=4` | `x+y+z=6; 2x-y+z=3; x+2y-z=4` | Exact |
| 64 | `x^2+y^2=25` | `x^{2}+y^{2}=25` | Exact |
| 65 | `x^2-y^2=9` | `x^2-y^2=0` | Top 8 |
| 66 | `xy=12; x+y=7` | `XY=12; x+y=7` | Exact |
| 67 | `xy+x+y=6` | `XY+X+Y=6` | Exact |
| 68 | `x^2+3x+2=0` | `x^{2}+3x+2=0` | Exact |
| 69 | `x^2-6x+8=0` | `x^{2}-6x+8=0` | Exact |
| 70 | `(x+1)^3=64` | `(x+1)^{3}=64` | Exact |
| 71 | `(2x-1)^3=27` | `(2x-1)^{3}=27` | Exact |
| 72 | `x^{2/3}=4` | `x^{2/3}=4` | Exact |
| 73 | `pi*x=6` | `\pi X=6` | Exact |
| 74 | `2*pi*r=22` | `2\pi r=22` | Exact |
| 75 | `pi*r^2=49*pi` | `\pi r^2=49\pi` | Exact |

## Improvements implemented

1. Added geometry-confirmed radical recovery: a radical-shaped stroke can correct OCR `r(...)` to
   `sqrt(...)` only when the original ink supplies the radical geometry.
2. Added geometry-confirmed absolute-value recovery for paired vertical bars that generic OCR reads
   as digit `1`.
3. Added spatial line segmentation before recognition. Each equation line is recognized
   independently and recombined as a system, preserving line boundaries.
4. Added π and multi-line support to the deterministic human-style stroke generator.
5. Added focused regression tests for radical and absolute-value repair.

## Honest limitations

- Rational cases 16–30 were drawn with handwritten slash notation and explicit grouping rather than
  stacked fraction bars. They test the same equation structure but are not a stacked-fraction visual
  benchmark.
- Four cases are correct only in the top eight: 4, 23, 57 and 65.
- Four cases remain absent from the top eight: 6, 33, 35 and 45.
- This report does not claim superiority over Mathpix. Mathpix was not run on the identical rendered
  stroke images under the same structural-equivalence rules.
