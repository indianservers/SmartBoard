# Smart Board Handwriting Recognition Audit

## 1. Executive summary

> **Run provenance:** Accuracy/status results use the conservative final scoring run. It processed
> 560 cases, executed recognition for 549, and marked 11 cases
> MANUAL_REVIEW_REQUIRED because the automated glyph writer could not represent them faithfully.
> Valid performance timing comes from the earlier uninterrupted identical-corpus run. The final
> accuracy run had an external emulator suspension and is not used for latency.


| Measure | Result |
|---|---:|
| Generated | 560 |
| Executed | 560 |
| Pass rate | 38.2% |
| Exact match | 34.8% |
| Semantic match | 38.9% |
| Average structural score | 77.5% |
| Average spatial score | 79.5% |
| Crashes | 0 |
| Timeouts | 30 |
| Conclusion | **NOT READY** |

## 2. Scope and methodology

The corpus contains 14 mandatory categories with 40 unique cases each. Automated cases replay stroke-level human-style ink through the production multimodal pipeline. Graph and geometry cases draw curves/diagrams and use the production graph/shape engines. Literal, semantic, structural and spatial scores are retained independently.

Device: manufacturer=Google, model=sdk_gphone64_x86_64, android=15, sdk=35, abi=x86_64|arm64-v8a, app_version=1.0 (1), recognition_model=TexTeller-Q4-v2:READY, input_mode=automated-stroke-replay, process_pss_start_kb=76026, process_pss_peak_kb=173304. Accuracy-run elapsed time: 6130110 ms (includes an external emulator suspension; use the dedicated performance metrics below for latency).

## 3. Overall results

| Category | Tests | Pass | Partial | Fail | Pass rate | Avg confidence | P95 time |
|---|---:|---:|---:|---:|---:|---:|---:|
| BASIC_ARITHMETIC | 40 | 24 | 10 | 6 | 60.0% | 0.941 | 4115 ms |
| ALGEBRAIC_EXPRESSIONS | 40 | 22 | 8 | 10 | 55.0% | 0.820 | 5305 ms |
| EQUATIONS_INEQUALITIES | 40 | 23 | 9 | 8 | 57.5% | 0.861 | 9139 ms |
| POWERS_SUBSCRIPTS_ROOTS | 40 | 16 | 6 | 18 | 40.0% | 0.830 | 23375 ms |
| FRACTIONS_RATIONAL | 40 | 28 | 4 | 8 | 70.0% | 0.846 | 4627 ms |
| COMPLEX_NUMBERS | 40 | 15 | 12 | 13 | 37.5% | 0.849 | 9710 ms |
| LOG_EXP_SPECIAL | 40 | 19 | 8 | 13 | 47.5% | 0.745 | 7500 ms |
| TRIGONOMETRY | 40 | 23 | 4 | 13 | 57.5% | 0.818 | 8867 ms |
| CALCULUS | 40 | 6 | 10 | 24 | 15.0% | 0.711 | 7701 ms |
| MATRICES_VECTORS | 40 | 4 | 10 | 26 | 10.0% | 0.725 | 25627 ms |
| GRAPHS | 40 | 10 | 0 | 30 | 25.0% | 0.911 | 6 ms |
| GEOMETRY_DIAGRAMS | 40 | 20 | 0 | 20 | 50.0% | 0.892 | 6 ms |
| PROBABILITY_STATISTICS | 40 | 3 | 7 | 30 | 7.5% | 0.680 | 28235 ms |
| SETS_LOGIC | 40 | 1 | 8 | 31 | 2.5% | 0.690 | 28030 ms |

## 4. Category analysis

### BASIC_ARITHMETIC

- Strength: basic_arithmetic-003.
- Weakness: basic_arithmetic-033.
- Most frequent error: DIGIT_CONFUSION (4 cases).
- Recommended correction: see the evidence-linked recommendation file.

### ALGEBRAIC_EXPRESSIONS

- Strength: algebraic_expressions-001.
- Weakness: algebraic_expressions-006.
- Most frequent error: LETTER_CONFUSION (13 cases).
- Recommended correction: see the evidence-linked recommendation file.

### EQUATIONS_INEQUALITIES

- Strength: equations_inequalities-025.
- Weakness: equations_inequalities-010.
- Most frequent error: LETTER_CONFUSION (5 cases).
- Recommended correction: see the evidence-linked recommendation file.

### POWERS_SUBSCRIPTS_ROOTS

- Strength: powers_subscripts_roots-005.
- Weakness: powers_subscripts_roots-004.
- Most frequent error: LETTER_CONFUSION (15 cases).
- Recommended correction: see the evidence-linked recommendation file.

### FRACTIONS_RATIONAL

- Strength: fractions_rational-003.
- Weakness: fractions_rational-018.
- Most frequent error: LETTER_CONFUSION (10 cases).
- Recommended correction: see the evidence-linked recommendation file.

### COMPLEX_NUMBERS

- Strength: complex_numbers-023.
- Weakness: complex_numbers-004.
- Most frequent error: LETTER_CONFUSION (21 cases).
- Recommended correction: see the evidence-linked recommendation file.

### LOG_EXP_SPECIAL

- Strength: log_exp_special-006.
- Weakness: log_exp_special-012.
- Most frequent error: LETTER_CONFUSION (20 cases).
- Recommended correction: see the evidence-linked recommendation file.

### TRIGONOMETRY

- Strength: trigonometry-019.
- Weakness: trigonometry-034.
- Most frequent error: LETTER_CONFUSION (14 cases).
- Recommended correction: see the evidence-linked recommendation file.

### CALCULUS

- Strength: calculus-006.
- Weakness: calculus-040.
- Most frequent error: LETTER_CONFUSION (29 cases).
- Recommended correction: see the evidence-linked recommendation file.

### MATRICES_VECTORS

- Strength: matrices_vectors-037.
- Weakness: matrices_vectors-006.
- Most frequent error: LETTER_CONFUSION (18 cases).
- Recommended correction: see the evidence-linked recommendation file.

### GRAPHS

- Strength: graphs-029.
- Weakness: graphs-004.
- Most frequent error: GRAPH_TYPE_WRONG (29 cases).
- Recommended correction: see the evidence-linked recommendation file.

### GEOMETRY_DIAGRAMS

- Strength: geometry_diagrams-029.
- Weakness: geometry_diagrams-001.
- Most frequent error: SHAPE_TYPE_WRONG (10 cases).
- Recommended correction: see the evidence-linked recommendation file.

### PROBABILITY_STATISTICS

- Strength: probability_statistics-024.
- Weakness: probability_statistics-005.
- Most frequent error: LETTER_CONFUSION (30 cases).
- Recommended correction: see the evidence-linked recommendation file.

### SETS_LOGIC

- Strength: sets_logic-017.
- Weakness: sets_logic-002.
- Most frequent error: LETTER_CONFUSION (30 cases).
- Recommended correction: see the evidence-linked recommendation file.

## 5. Expected versus detected examples

### 20 correct examples

- **basic_arithmetic-001** - Expected `7`; detected `7`; normalized `7`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-002** - Expected `108`; detected `108`; normalized `108`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-003** - Expected `-42`; detected `-42`; normalized `-42`; confidence `1.0`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-005** - Expected `12+8`; detected `12+8`; normalized `12+8`; confidence `1.0`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-006** - Expected `95-47`; detected `95-47`; normalized `95-47`; confidence `0.9713`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-007** - Expected `13*9`; detected `13 \times 9`; normalized `13*9`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-008** - Expected `144/12`; detected `144/12`; normalized `144/12`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-009** - Expected `8(4+3)`; detected `8(4+3)`; normalized `8(4+3)`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-011** - Expected `3:5`; detected `3:5`; normalized `3:5`; confidence `1.0`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-012** - Expected `18-(-7)`; detected `18-(-7)`; normalized `18-(-7)`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-013** - Expected `12+8*4-6/3`; detected `12+8 \times 4-6/3`; normalized `12+8*4-6/3`; confidence `0.8818135`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-014** - Expected `(36+12)/6`; detected `(36+12)/6`; normalized `(36+12)/6`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-018** - Expected `1/2+3/4`; detected `1/2+3/4`; normalized `1/2+3/4`; confidence `0.85151327`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-019** - Expected `100-99+98-97`; detected `100-99+98-97`; normalized `100-99+98-97`; confidence `0.95945996`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-020** - Expected `2(3+4(5-2))`; detected `2(3+4(5-2))`; normalized `2(3+4(5-2))`; confidence `0.9221`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-021** - Expected `9999+1`; detected `9999+1`; normalized `9999+1`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-025** - Expected `1-2-3-4`; detected `1-2-3-4`; normalized `1-2-3-4`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-027** - Expected `8/4/2`; detected `8/4/2`; normalized `8/4/2`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-028** - Expected `(1+2)(3+4)`; detected `(1+2)(3+4)`; normalized `(1+2)(3+4)`; confidence `0.93865997`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-030** - Expected `5:7=10:14`; detected `5:7=10:14`; normalized `5:7=10:14`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.

### 30 partial examples

- **basic_arithmetic-004** - Expected `3.14159`; detected `3_.14159`; normalized `3_.14159`; confidence `1.0`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-004.png`.
- **basic_arithmetic-015** - Expected `[14-3]*5`; detected `[1 \times-3] \times 5`; normalized `[1*-3]*5`; confidence `0.86352646`; status **PARTIAL**; errors `DIGIT_CONFUSION`; evidence `failures/basic_arithmetic-015.png`.
- **basic_arithmetic-016** - Expected `0.006+1.04`; detected `0_.006+1_.04`; normalized `0_.006+1_.04`; confidence `1.0`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-016.png`.
- **basic_arithmetic-017** - Expected `-7*-8`; detected `-7x-8`; normalized `-7x-8`; confidence `0.88817155`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-017.png`.
- **basic_arithmetic-022** - Expected `6.02*10^23`; detected `6_.02*10^23`; normalized `6_.02*10^23`; confidence `0.98786104`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-022.png`.
- **basic_arithmetic-024** - Expected `7.0-0.07`; detected `7_.0-0_.07`; normalized `7_.0-0_.07`; confidence `0.95995367`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-024.png`.
- **basic_arithmetic-026** - Expected `2--3`; detected `2-3`; normalized `2-3`; confidence `0.80290616`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-026.png`.
- **basic_arithmetic-032** - Expected `0.333...`; detected `0_.333_(...)`; normalized `0_.333_(...)`; confidence `1.0`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-032.png`.
- **basic_arithmetic-035** - Expected `2^10`; detected `2^70`; normalized `2^70`; confidence `0.81668`; status **PARTIAL**; errors `DIGIT_CONFUSION`; evidence `failures/basic_arithmetic-035.png`.
- **basic_arithmetic-039** - Expected `7+?=12`; detected `7+=12`; normalized `7+=12`; confidence `1.0`; status **PARTIAL**; errors ``; evidence `failures/basic_arithmetic-039.png`.
- **algebraic_expressions-007** - Expected `(x+y)^2`; detected `(x+y)^?`; normalized `(x+y)^?`; confidence `0.356`; status **PARTIAL**; errors `DIGIT_CONFUSION|LOW_CONFIDENCE`; evidence `failures/algebraic_expressions-007.png`.
- **algebraic_expressions-012** - Expected `p(q-r)+s`; detected `p(-r)+s`; normalized `p(-r)+s`; confidence `0.8959505`; status **PARTIAL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-012.png`.
- **algebraic_expressions-014** - Expected `l^2+1`; detected `1^2+1`; normalized `1^2+1`; confidence `0.91104615`; status **PARTIAL**; errors `DIGIT_CONFUSION|LETTER_CONFUSION`; evidence `failures/algebraic_expressions-014.png`.
- **algebraic_expressions-023** - Expected `a^{m+n}`; detected `a^{n+n}`; normalized `a^(n+n)`; confidence `0.8734875`; status **PARTIAL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-023.png`.
- **algebraic_expressions-025** - Expected `a_n=a_1+(n-1)d`; detected `a_n=a_{1}+(n-1)a`; normalized `a_n=a_1+(n-1)a`; confidence `0.8394914`; status **PARTIAL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-025.png`.
- **algebraic_expressions-029** - Expected `{x+1,x<0;x^2,x>=0}`; detected `\left \{ x+1,x<0;x^2,x>=0 \right \}`; normalized `\(x+1,x<0;x^2,x>=0\)`; confidence `0.88963985`; status **PARTIAL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-029.png`.
- **algebraic_expressions-037** - Expected `(x+3)^2 -> x^2+6x+9`; detected `(x+3)^2->x^2+6x+0`; normalized `(x+3)^2->x^2+6x+0`; confidence `0.80467993`; status **PARTIAL**; errors `DIGIT_CONFUSION`; evidence `failures/algebraic_expressions-037.png`.
- **algebraic_expressions-039** - Expected `2x+3y\n-4x+y`; detected `2X+3Y;-4X+Y`; normalized `2X+3Y;-4X+Y`; confidence `1.0`; status **PARTIAL**; errors ``; evidence `failures/algebraic_expressions-039.png`.
- **equations_inequalities-009** - Expected `x+y=10\n2x-y=3`; detected `x+y=10;2x-y=3`; normalized `x+y=10;2x-y=3`; confidence `0.8961`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-009.png`.
- **equations_inequalities-018** - Expected `x^4-16=0`; detected `x^{4}-15=0`; normalized `x^(4)-15=0`; confidence `0.9209706`; status **PARTIAL**; errors `DIGIT_CONFUSION`; evidence `failures/equations_inequalities-018.png`.
- **equations_inequalities-027** - Expected `x+y=5\nx-y=1\nx=3`; detected `x+y=5;X-Y=1;x=3`; normalized `x+y=5;X-Y=1;x=3`; confidence `0.8961`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-027.png`.
- **equations_inequalities-028** - Expected `{x+y=4;2x-y=5}`; detected `\{X+Y=4;2X-Y=5 \}`; normalized `\(X+Y=4;2X-Y=5\)`; confidence `0.8405182`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-028.png`.
- **equations_inequalities-035** - Expected `x+1=2\nx=1`; detected `x+1=2;X=1`; normalized `x+1=2;X=1`; confidence `0.990852`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-035.png`.
- **equations_inequalities-036** - Expected `2x+2=10\n2x=8\nx=4`; detected `2x+2=10;2X=8;x=4`; normalized `2x+2=10;2X=8;x=4`; confidence `0.8850431`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-036.png`.
- **equations_inequalities-037** - Expected `x+5=12\nx=12-5\nx=7`; detected `x+5=12;x=12-5;x=7`; normalized `x+5=12;x=12-5;x=7`; confidence `0.9021286`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-037.png`.
- **equations_inequalities-038** - Expected `x+5=12\nx=12+5\nx=17`; detected `x+5=12;x=12+5;x=17`; normalized `x+5=12;x=12+5;x=17`; confidence `0.9221`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-038.png`.
- **equations_inequalities-040** - Expected `x+y=3\n  x-y=1`; detected `x+y=3;X-Y=1`; normalized `x+y=3;X-Y=1`; confidence `0.8961`; status **PARTIAL**; errors ``; evidence `failures/equations_inequalities-040.png`.
- **powers_subscripts_roots-012** - Expected `6.02*10^23`; detected `6_.02*10^23`; normalized `6_.02*10^23`; confidence `0.96407986`; status **PARTIAL**; errors ``; evidence `failures/powers_subscripts_roots-012.png`.
- **powers_subscripts_roots-025** - Expected `1.5*10^{-4}`; detected `1_.5*10^(-4)`; normalized `1_.5*10^(-4)`; confidence `1.0`; status **PARTIAL**; errors ``; evidence `failures/powers_subscripts_roots-025.png`.
- **powers_subscripts_roots-031** - Expected `x^{2^3}`; detected `X^{23}3`; normalized `X^(23)3`; confidence `0.8914158`; status **PARTIAL**; errors `DIGIT_CONFUSION`; evidence `failures/powers_subscripts_roots-031.png`.

### 50 important failures

- **basic_arithmetic-010** - Expected `25%`; detected `25`; normalized `25`; confidence `0.9841`; status **WRONG_SYMBOL**; errors ``; evidence `failures/basic_arithmetic-010.png`.
- **basic_arithmetic-023** - Expected `0/7`; detected `0.17`; normalized `0.17`; confidence `0.87689614`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|FRACTION_MISREAD`; evidence `failures/basic_arithmetic-023.png`.
- **basic_arithmetic-029** - Expected `12.5%`; detected `12_.5`; normalized `12_.5`; confidence `1.0`; status **WRONG_SYMBOL**; errors ``; evidence `failures/basic_arithmetic-029.png`.
- **basic_arithmetic-031** - Expected `1,024+2,048`; detected `1_.024+2_.048`; normalized `1_.024+2_.048`; confidence `1.0`; status **WRONG_SYMBOL**; errors ``; evidence `failures/basic_arithmetic-031.png`.
- **basic_arithmetic-033** - Expected ``; detected ``; normalized ``; confidence ``; status **MANUAL_REVIEW_REQUIRED**; errors ``; evidence `failures/basic_arithmetic-033.png`.
- **basic_arithmetic-036** - Expected `sqrt(144)`; detected `55+ \frac{5}{2}44`; normalized `55+((5)/(2))44`; confidence `0.6489037`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|ROOT_SCOPE_ERROR`; evidence `failures/basic_arithmetic-036.png`.
- **algebraic_expressions-005** - Expected `a(b+c)-d`; detected `a+b+c \geq-a`; normalized `a+b+c>=-a`; confidence `0.82336235`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-005.png`.
- **algebraic_expressions-006** - Expected ``; detected ``; normalized ``; confidence ``; status **MANUAL_REVIEW_REQUIRED**; errors ``; evidence `failures/algebraic_expressions-006.png`.
- **algebraic_expressions-016** - Expected `q^2-9q`; detected `x^2-2x`; normalized `x^2-2x`; confidence `0.7423849`; status **WRONG_SYMBOL**; errors `DIGIT_CONFUSION|LETTER_CONFUSION`; evidence `failures/algebraic_expressions-016.png`.
- **algebraic_expressions-017** - Expected `S_5+5S`; detected `5+5`; normalized `5+5`; confidence `0.9221`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|SUBSCRIPT_MISSED`; evidence `failures/algebraic_expressions-017.png`.
- **algebraic_expressions-019** - Expected `(a+(b-c))^2`; detected `(O+(D-C))^?`; normalized `(O+(D-C))^?`; confidence `0.587`; status **WRONG_SYMBOL**; errors `DIGIT_CONFUSION|LETTER_CONFUSION`; evidence `failures/algebraic_expressions-019.png`.
- **algebraic_expressions-028** - Expected `max(a,b)-min(a,b)`; detected `Maxco,b)-Minco,DJ`; normalized `Maxco,b)-Minco,DJ`; confidence `0.4854`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION|LOW_CONFIDENCE`; evidence `failures/algebraic_expressions-028.png`.
- **algebraic_expressions-030** - Expected ``; detected ``; normalized ``; confidence ``; status **MANUAL_REVIEW_REQUIRED**; errors ``; evidence `failures/algebraic_expressions-030.png`.
- **algebraic_expressions-033** - Expected `A(B+C)-AB`; detected `( \lambda C)-`; normalized `(lambdaC)-`; confidence `0.69443446`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-033.png`.
- **algebraic_expressions-034** - Expected `alpha*x+beta`; detected `a+b`; normalized `a+b`; confidence `0.6484669`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION`; evidence `failures/algebraic_expressions-034.png`.
- **algebraic_expressions-038** - Expected `x+~~3~~4`; detected `x+34`; normalized `x+34`; confidence `0.97581196`; status **WRONG_SYMBOL**; errors ``; evidence `failures/algebraic_expressions-038.png`.
- **equations_inequalities-006** - Expected `sqrt(x+4)=x-2`; detected `s \cap t(X+4)=X-2`; normalized `s\capt(X+4)=X-2`; confidence `0.76195616`; status **WRONG_LAYOUT**; errors `LETTER_CONFUSION|ROOT_SCOPE_ERROR`; evidence `failures/equations_inequalities-006.png`.
- **equations_inequalities-010** - Expected `x+y+z=6\n2x-y+z=3\nx+2y-z=4`; detected ``; normalized ``; confidence ``; status **TIMEOUT**; errors `TIMEOUT`; evidence `failures/equations_inequalities-010.png`.
- **equations_inequalities-011** - Expected ``; detected ``; normalized ``; confidence ``; status **MANUAL_REVIEW_REQUIRED**; errors ``; evidence `failures/equations_inequalities-011.png`.
- **equations_inequalities-014** - Expected `x!=4`; detected `x \cdot x \cdot!=4`; normalized `x*x*!=4`; confidence `0.6646955`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION`; evidence `failures/equations_inequalities-014.png`.
- **equations_inequalities-024** - Expected `sqrt(x)+sqrt(x-1)=3`; detected `\sin(X)+\sin(X-1)=3`; normalized `sin(X)+sin(X-1)=3`; confidence `0.874069`; status **WRONG_LAYOUT**; errors `LETTER_CONFUSION|ROOT_SCOPE_ERROR`; evidence `failures/equations_inequalities-024.png`.
- **equations_inequalities-030** - Expected `(x-1)^2>=0`; detected `(X-132)=0`; normalized `(X-132)=0`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|SUPERSCRIPT_MISSED|LOW_CONFIDENCE`; evidence `failures/equations_inequalities-030.png`.
- **equations_inequalities-033** - Expected `F=ma`; detected `IMA`; normalized `IMA`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|OVERWRITING_ERROR|LOW_CONFIDENCE`; evidence `failures/equations_inequalities-033.png`.
- **equations_inequalities-034** - Expected `a/b=c/d`; detected `a\times b=c \times d`; normalized `a*b=c*d`; confidence `0.6334475`; status **WRONG_LAYOUT**; errors `LETTER_CONFUSION|FRACTION_MISREAD|STROKE_ORDER_SENSITIVE`; evidence `failures/equations_inequalities-034.png`.
- **powers_subscripts_roots-004** - Expected `x^{-2}`; detected ``; normalized ``; confidence ``; status **TIMEOUT**; errors `TIMEOUT`; evidence `failures/powers_subscripts_roots-004.png`.
- **powers_subscripts_roots-006** - Expected `a^{b+c}`; detected ``; normalized ``; confidence ``; status **TIMEOUT**; errors `TIMEOUT`; evidence `failures/powers_subscripts_roots-006.png`.
- **powers_subscripts_roots-008** - Expected `sqrt(x)`; detected `\sin(X)`; normalized `sin(X)`; confidence `0.8506765`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|ROOT_SCOPE_ERROR`; evidence `failures/powers_subscripts_roots-008.png`.
- **powers_subscripts_roots-009** - Expected `sqrt(x^2+y^2)`; detected `\sin^{2}+(x^{2}+y^{2})`; normalized `sin^(2)+(x^(2)+y^(2))`; confidence `0.88295126`; status **WRONG_LAYOUT**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|ROOT_SCOPE_ERROR`; evidence `failures/powers_subscripts_roots-009.png`.
- **powers_subscripts_roots-010** - Expected `root_3(27)`; detected `r00+3(27)`; normalized `r00+3(27)`; confidence `0.83350784`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|SUBSCRIPT_MISSED|LOCATION_SENSITIVE|STROKE_ORDER_SENSITIVE`; evidence `failures/powers_subscripts_roots-010.png`.
- **powers_subscripts_roots-011** - Expected `root_4(16)`; detected `root_al163`; normalized `root_al163`; confidence `0.7639011`; status **WRONG_SYMBOL**; errors `DIGIT_CONFUSION|LETTER_CONFUSION`; evidence `failures/powers_subscripts_roots-011.png`.
- **powers_subscripts_roots-015** - Expected `a_{n+1}`; detected `ab+1`; normalized `ab+1`; confidence `0.86953646`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|SUBSCRIPT_MISSED|OVERWRITING_ERROR`; evidence `failures/powers_subscripts_roots-015.png`.
- **powers_subscripts_roots-017** - Expected `x^{y^2}`; detected `X^{YZ}3`; normalized `X^(YZ)3`; confidence `0.84823817`; status **WRONG_SYMBOL**; errors `DIGIT_CONFUSION|LETTER_CONFUSION`; evidence `failures/powers_subscripts_roots-017.png`.
- **powers_subscripts_roots-018** - Expected `(a^m)^n`; detected `a`; normalized `a`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|SUPERSCRIPT_MISSED|STROKE_ORDER_SENSITIVE|LOW_CONFIDENCE`; evidence `failures/powers_subscripts_roots-018.png`.
- **powers_subscripts_roots-020** - Expected `9^{3/2}`; detected `9^{312}`; normalized `9^(312)`; confidence `0.89636284`; status **WRONG_LAYOUT**; errors `DIGIT_CONFUSION|FRACTION_MISREAD`; evidence `failures/powers_subscripts_roots-020.png`.
- **powers_subscripts_roots-021** - Expected `sqrt[3](x+1)`; detected `sr+[3](x+1)`; normalized `sr+[3](x+1)`; confidence `0.92059255`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|ROOT_SCOPE_ERROR|LOCATION_SENSITIVE`; evidence `failures/powers_subscripts_roots-021.png`.
- **powers_subscripts_roots-022** - Expected `sqrt(sqrt(x))`; detected `Sr+(srt(x))`; normalized `Sr+(srt(x))`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|ROOT_SCOPE_ERROR|LOCATION_SENSITIVE|LOW_CONFIDENCE`; evidence `failures/powers_subscripts_roots-022.png`.
- **powers_subscripts_roots-023** - Expected `sqrt(x+sqrt(y))`; detected `srcx+srty>>`; normalized `srcx+srty>>`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|ROOT_SCOPE_ERROR|LOW_CONFIDENCE`; evidence `failures/powers_subscripts_roots-023.png`.
- **powers_subscripts_roots-026** - Expected `H_2O`; detected `frac{}`; normalized `frac()`; confidence `0.60892224`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|SUBSCRIPT_MISSED|STROKE_ORDER_SENSITIVE`; evidence `failures/powers_subscripts_roots-026.png`.
- **powers_subscripts_roots-027** - Expected `CO_2`; detected ``; normalized ``; confidence ``; status **TIMEOUT**; errors `TIMEOUT`; evidence `failures/powers_subscripts_roots-027.png`.
- **powers_subscripts_roots-029** - Expected `T_i^{jk}`; detected `i`; normalized `i`; confidence `0.7249349`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|SUPERSCRIPT_MISSED|SUBSCRIPT_MISSED`; evidence `failures/powers_subscripts_roots-029.png`.
- **powers_subscripts_roots-034** - Expected `e^{-x^2}`; detected `e`; normalized `e`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|SUPERSCRIPT_MISSED|STROKE_ORDER_SENSITIVE|LOW_CONFIDENCE`; evidence `failures/powers_subscripts_roots-034.png`.
- **powers_subscripts_roots-038** - Expected `x^ 2`; detected `times`; normalized `times`; confidence `0.8089198`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|SUPERSCRIPT_MISSED`; evidence `failures/powers_subscripts_roots-038.png`.
- **fractions_rational-005** - Expected `(a^2-b^2)/(a+b)`; detected `co-DJI ca+b)`; normalized `co-DJIca+b)`; confidence `0.59328`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|SUPERSCRIPT_MISSED|FRACTION_MISREAD`; evidence `failures/fractions_rational-005.png`.
- **fractions_rational-007** - Expected `sqrt(x)/(x+2)`; detected `\sin(X)/(X+2)`; normalized `sin(X)/(X+2)`; confidence `0.84117204`; status **WRONG_LAYOUT**; errors `LETTER_CONFUSION|FRACTION_MISREAD|ROOT_SCOPE_ERROR`; evidence `failures/fractions_rational-007.png`.
- **fractions_rational-013** - Expected `dy/dx`; detected `\alpha Y/ \alpha X`; normalized `\alphaY/\alphaX`; confidence `0.8404896`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION`; evidence `failures/fractions_rational-013.png`.
- **fractions_rational-018** - Expected `(1+sqrt(5))/2`; detected `ass`; normalized `ass`; confidence `0.439`; status **WRONG_STRUCTURE**; errors `DIGIT_CONFUSION|LETTER_CONFUSION|FRACTION_MISREAD|ROOT_SCOPE_ERROR|STROKE_ORDER_SENSITIVE|LOW_CONFIDENCE`; evidence `failures/fractions_rational-018.png`.
- **fractions_rational-022** - Expected `(a+b)/(c+d)+(e+f)/(g+h)`; detected `(otos/ccta)+lett)/(gth)`; normalized `(otos/ccta)+lett)/(gth)`; confidence `0.45639998`; status **WRONG_SYMBOL**; errors `LETTER_CONFUSION|LOW_CONFIDENCE`; evidence `failures/fractions_rational-022.png`.
- **fractions_rational-026** - Expected `sqrt(x/y)`; detected `\sin^{t}(X/Y)`; normalized `sin^(t)(X/Y)`; confidence `0.723158`; status **WRONG_LAYOUT**; errors `LETTER_CONFUSION|FRACTION_MISREAD|ROOT_SCOPE_ERROR|STROKE_ORDER_SENSITIVE`; evidence `failures/fractions_rational-026.png`.
- **fractions_rational-027** - Expected `sqrt(x)/sqrt(y)`; detected `\int S\Gamma t(X)/S\Gamma t(Y)`; normalized `\intS\Gammat(X)/S\Gammat(Y)`; confidence `0.8027644`; status **WRONG_LAYOUT**; errors `LETTER_CONFUSION|FRACTION_MISREAD|ROOT_SCOPE_ERROR`; evidence `failures/fractions_rational-027.png`.
- **fractions_rational-029** - Expected `(sum_{i=1}^n i)/n`; detected `(Sumi=1' is in`; normalized `(Sumi=1'isin`; confidence `0.5608`; status **WRONG_STRUCTURE**; errors `LETTER_CONFUSION|SUPERSCRIPT_MISSED|SUBSCRIPT_MISSED|FRACTION_MISREAD`; evidence `failures/fractions_rational-029.png`.

## 6. Symbol confusion analysis

See `../results/SMART_BOARD_SYMBOL_CONFUSION_MATRIX.csv`; pairs are ranked by observed frequency and impact.

## 7. Layout-recognition analysis

Independent spatial scores cover superscripts, subscripts, fraction bars, root scope, matrices, multiline grouping, graph classification and diagram classification. Failures retain layout errors even where conservative semantic normalization matches.

## 8. Environmental sensitivity

Profiles, regions and stroke variants are recorded per case in the results CSV. Physical stylus/finger and multi-device conclusions require Mode B manual runs and are not inferred from automated replay.

## 9. Performance results

Accuracy and status metrics come from the conservative final run. That run was externally suspended
for about one hour, so its wall-clock total and one latency sample are not used for performance.
Performance comes from the earlier uninterrupted run over the identical 560 stroke cases:

| Metric | Result |
|---|---:|
| Median | 3338 ms |
| P90 | 5908 ms |
| P95 | 9941 ms |
| P99 | 28810 ms |
| Mean | 4211.9 ms |
| Maximum | 31476 ms |
| Timeouts | 28 |
| Crashes | 0 |

The production provider performs blocking inference, so coroutine cancellation did not always stop
work at the requested 10-second deadline; P99 and maximum latency therefore exceed the target.

## 10. Root-cause analysis

Observed failures are classified by symbol, spatial hierarchy, grouping, graph/shape classification and environment sensitivity. The raw result is never modified before persistence.

## 11. Prioritized recommendations

See `SMART_BOARD_RECOMMENDATIONS.md` for evidence-linked P0-P4 actions.

## 12. Production-readiness conclusion

**NOT READY** based on the measured automated run. Manual handwriting, stylus/finger, phone/tablet and multi-Android-version coverage remain separate evidence requirements.
