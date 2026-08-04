# Handwritten algebra recognition accuracy report

Test date: 2026-08-05
Device: Android Emulator `emulator-5554`, API 35
Corpus: 80 pressure-varying, deterministically jittered handwritten expressions from the supplied sheet
Engine: digital ink + dedicated offline TexTeller formula vision + parser/structure fusion

## Improvement summary

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Primary structurally correct | 7/80 (8.75%) | **58/80 (72.50%)** | **+63.75 points** |
| Correct within top 8 | 11/80 (13.75%) | **59/80 (73.75%)** | **+60.00 points** |
| Primary failures | 73 | **22** | **51 fewer** |
| Crash status | No crash | **No crash** | — |

“Correct” ignores harmless case, whitespace, LaTeX braces and equivalent command formatting such as
`\times`, but requires the same symbols, operators, powers, subscripts, grouping and fraction
structure. It does not award credit for merely returning parseable mathematics.

## Expected versus detected after improvement

| # | Expected | Detected primary | Result |
|---:|---|---|:---:|
| 1 | `3x^2+4x-7` | `3X^{2}+4X-7` | Exact |
| 2 | `a^2-5a+6` | `a^{2}-5a+6` | Exact |
| 3 | `2m^3-7m^2+4m-1` | `2M^{3}-7M^{2}+4M-1` | Exact |
| 4 | `5p^4+3p^2-p+8` | `5p^4+3p^2-p+8` | Exact |
| 5 | `x^2y+3xy^2-4y` | `X^{2}Y+3XY^{2}-4Y` | Exact |
| 6 | `7a^3b^2-2ab+5` | `7a^{3}b^{2}-20b+5` | Miss |
| 7 | `(x+3)^2` | `(X+3)^2` | Exact |
| 8 | `(2y-5)^3` | `(2Y-5)^3` | Exact |
| 9 | `(3a+2b)(a-b)` | `(3a+2b)(a-b)` | Exact |
| 10 | `(x^2-4)(x+2)` | `(x^2-4)(x+2)` | Exact |
| 11 | `(p^2+3p+2)/(p+1)` | `(p^2+3p+2)/(p+1)` | Exact |
| 12 | `(2x^3-5x^2+7x)/x` | `(2X^3-5X^2+7X)/X` | Exact |
| 13 | `x^{-2}+3x^{-1}-4` | `X^{-2}+3X^{-1}-4` | Exact |
| 14 | `(a^{-1}+b^{-1})^2` | `(a^{-1}+b^{-1})^{2}` | Exact |
| 15 | `(x^3y^{-2}z)^2` | `(X^3Y^{-2}Z)^2` | Exact |
| 16 | `a^{3/2}+2a^{1/2}-1` | `a^{3/2}+2a^{1/2}-1` | Exact |
| 17 | `(x^{1/2}-y^{1/2})^2` | `(X^{1/2}-Y^{1/2})^{2}` | Exact |
| 18 | `(x^2-y^2)/(x+y)` | `(x^2-y^2)/(X+Y)` | Exact |
| 19 | `(a^3+b^3)/(a+b)` | `cost basica+b)` | Miss |
| 20 | `(x-y)^2-(x+y)^2` | `(X-Y)^2-(X+Y)^2` | Exact |
| 21 | `2x^2+3x^{-1}-5x^{-2}` | `2x^{2}+3x^{-1}-5x^{-2}` | Exact |
| 22 | `(x^2+1)(x^2-1)` | `(x^{2}+1)(x^{2}-1)` | Exact |
| 23 | `x^4-5x^2+4` | `x^{4}-5x^{2}+4` | Exact |
| 24 | `(a+b)^3` | `3(0+b^)` | Miss |
| 25 | `(a-b)^3` | `(a-b)^3` | Exact |
| 26 | `x^2+1/x^2` | `x^2+1/X^2` | Exact |
| 27 | `a^2+b^2+2ab` | `a^2+b^2+2ab` | Exact |
| 28 | `a^2+b^2-2ab` | `a^2+b^2-2ab` | Exact |
| 29 | `(x+1/x)^2` | `(x+1/x)^{2}` | Exact |
| 30 | `(a+1/a)^3` | `(O+1/0)^"` | Miss |
| 31 | `(x^3-1)/(x-1)` | `(X^{3}-1)/(X-1)` | Exact |
| 32 | `(x^3+1)/(x+1)` | `(X^{3}+1)/(X+1)` | Exact |
| 33 | `x^5-x^3+x` | `x^{5}-x^{3}+x` | Exact |
| 34 | `8x^3+27y^3` | `8X^{3}+27Y^{3}` | Exact |
| 35 | `27a^3-64b^3` | `270^{3}-640^{3}` | Miss |
| 36 | `(2x+3y)^2` | `(2x+3Y)^2` | Exact |
| 37 | `(3a-2b)^2` | `(3a-2b)^2` | Exact |
| 38 | `(x+y)(x^2-xy+y^2)` | `(X+Y)(X^2-XY+Y^2)` | Exact |
| 39 | `(x-y)(x^2+xy+y^2)` | `(X-Y)(X^2+XY+Y^2)` | Exact |
| 40 | `x^{10}+x^5+1` | `x^{10}+x^{5}+1` | Exact |
| 41 | `(x^2+2x+1)^3` | `(x^{2}+2x+1)^{3}` | Exact |
| 42 | `(2a^2-3b^3)^2` | `(2a^2-3b^3)^2` | Exact |
| 43 | `(2x^{-2}y^3)/(3x^2y^{-1})` | `(2X^{-2}Y^{3})/(3X^{2}Y^{-1})` | Exact |
| 44 | `((x^2y^{-3})^2)/(x^{-1}y^4)` | `((X^2Y^{-3})^2)/(X^{-1}Y^4)` | Exact |
| 45 | `(a^m)^n` | `(a^n)^n` | Miss |
| 46 | `a^m*a^n` | `axon` | Miss |
| 47 | `a^m/a^n` | `o^m/a^n` | Top 8 |
| 48 | `(ab)^n` | `(a \theta)^n` | Miss |
| 49 | `(a/b)^n` | `Colby^a` | Miss |
| 50 | `a^0+a^1+a^2+...+a^n` | `go tot+02+...ton` | Miss |
| 51 | `(1-r^{n+1})/(1-r)` | `(1-r^{n+1})/(1-r)` | Exact |
| 52 | `x^n-y^n` | `X^n-Y^n` | Exact |
| 53 | `x^n+y^n` | `X^n+Y^n` | Exact |
| 54 | `(x+1)^n` | `(x+1)^{n}` | Exact |
| 55 | `(1-x)^n` | `(1-X)^{n}` | Exact |
| 56 | `C(n,r)=n!/(r!(n-r)!)` | `c(n,r)=n!/(r!(n-r)!)` | Exact |
| 57 | `(x+y)^n` | `(X+Y)^n` | Exact |
| 58 | `(x-y)^n` | `(X-Y)^n` | Exact |
| 59 | `∑_{k=1}^{n}k^2` | `\sum k=1^{n}k^{2}` | Miss |
| 60 | `∑_{k=1}^{n}k^3` | `\sum k=1^{n}k^{3}` | Miss |
| 61 | `x_i^2+y_j^2` | `x_{i}^{2}+y_{j}^{2}` | Exact |
| 62 | `∑_{i=1}^{n}x_i` | `\sum i=1^{n}X_i` | Miss |
| 63 | `∏_{i=1}^{n}a_i` | `n_{i}=1^{n}a_{i}` | Miss |
| 64 | `a_{n+1}=a_n+d` | `a_{n+1}=a_{n}+a_{n}` | Miss |
| 65 | `a_n=a_1+(n-1)d` | `a_{n}=a_{1}+(n-1)a` | Miss |
| 66 | `b_{n+1}=r*b_n` | `b_{n+1}=r \times b_{n}` | Exact |
| 67 | `b_n=b_1*r^{n-1}` | `b_n=b_1 \times r^{n-1}` | Exact |
| 68 | `log_a(xy)=log_a(x)+log_a(y)` | `109o(XY)=l09_0(X)+L09a(Y)` | Miss |
| 69 | `log_a(x/y)=log_a(x)-log_a(y)` | `\log_a(X/Y)=\log_a(X)-\log_a(Y)` | Exact |
| 70 | `log_a(x^n)=n*log_a(x)` | `\log_{0}(X^{n})=n\times\log_{0}(X)` | Miss |
| 71 | `e^{x+y}=e^x*e^y` | `e^{x+y}=e^{x}xe^{y}` | Miss |
| 72 | `e^{x-y}=e^x/e^y` | `e^{x-y}=e^{x}/e^{y}` | Exact |
| 73 | `ln(xy)=ln(x)+ln(y)` | `ln(XY)=ln(X)+ln(Y)` | Exact |
| 74 | `ln(x/y)=ln(x)-ln(y)` | `ln(X/Y)=ln(X)-ln(Y)` | Exact |
| 75 | `e^{ln(x)}=x` | `e^{\ln(x)}=x` | Exact |
| 76 | `sin^2(x)+cos^2(x)=1` | `\sin^2(X)+\cos^2(X)=1` | Exact |
| 77 | `1+tan^2(x)=sec^2(x)` | `1+ton2(x)=sec<x>` | Miss |
| 78 | `1+cot^2(x)=csc^2(x)` | `1+cor'(x)=(sc(x)` | Miss |
| 79 | `sin(x+y)=sin(x)cos(y)+cos(x)sin(y)` | `\sin(X+Y)=\sin(X)\cos(Y)+\cos(X)\sin(Y)` | Exact |
| 80 | `cos(x+y)=cos(x)cos(y)-sin(x)sin(y)` | `\cos(X+Y)=\cos(X)\cos(Y)-\sin(X)\sin(Y)` | Exact |

## Changes made

1. Extended the emulator corpus from 75 to all 80 numbered expressions, including identities 76–80.
2. Exercised and cached the optional 255,615,006-byte TexTeller ONNX formula model in the emulator's app-private storage.
3. Changed fusion calibration so the dedicated mathematical vision model outranks differently calibrated generic handwriting when it is available; generic recognition remains in alternatives.
4. Fixed LaTeX-to-engine normalization for functions with subscripts, including `\log_a`.
5. Corrected evaluation so equivalent LaTeX formatting is not counted as an OCR failure.
6. Added regression coverage for dedicated-formula priority and subscripted function conversion.

## Remaining error clusters

| Cluster | Examples | Required next improvement |
|---|---|---|
| Similar handwritten glyphs | `a→0`, `b→0/θ`, `m→n`, `d→a` | Personal glyph adaptation using multiple independently written samples |
| Large operators and limits | Cases 59–63 | Dedicated spatial grammar for `∑`, `∏`, upper and lower limits |
| Dense superscript variables | Cases 45–50 | Better small-glyph segmentation before model fusion |
| Short trig tokens | Cases 77–78 | Token-level confidence and alternatives for `tan/sec/cot/csc` |
| Implicit multiplication | Case 71 | Geometry-aware distinction between handwritten `×` and variable `x` |

## Reproduction

The model was installed and the full corpus was executed directly on `emulator-5554`. The reusable
test also supports `caseStart` and `caseLimit` instrumentation arguments for focused regression runs.

This result is not claimed to be better than Mathpix because Mathpix was not run on the identical
80 rendered inputs. A defensible comparison requires the same images, the same structural
equivalence rules and published latency/cost measurements for both engines.
