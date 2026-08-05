# Smart Board Handwriting Recognition Audit

## 1. Executive summary

| Measure | Result |
|---|---:|
| Generated | 560 |
| Executed | 560 |
| Pass rate | 37.3% |
| Exact match | 34.8% |
| Semantic match | 44.3% |
| Average structural score | 62.9% |
| Average spatial score | 81.1% |
| Crashes | 0 |
| Timeouts | 28 |
| Conclusion | **NOT READY** |

## 2. Scope and methodology

The corpus contains 14 mandatory categories with 40 unique cases each. Automated cases replay stroke-level human-style ink through the production multimodal pipeline. Graph and geometry cases draw curves/diagrams and use the production graph/shape engines. Literal, semantic, structural and spatial scores are retained independently.

Device: manufacturer=Google, model=sdk_gphone64_x86_64, android=15, sdk=35, abi=x86_64|arm64-v8a, app_version=1.0 (1), recognition_model=TexTeller-Q4-v2:READY, input_mode=automated-stroke-replay, process_pss_start_kb=75273, process_pss_peak_kb=166377. Total elapsed time: 2377461 ms.

## 3. Overall results

| Category | Tests | Pass | Partial | Fail | Pass rate | Avg confidence | P95 time |
|---|---:|---:|---:|---:|---:|---:|---:|
| BASIC_ARITHMETIC | 40 | 8 | 3 | 29 | 20.0% | 0.941 | 4297 ms |
| ALGEBRAIC_EXPRESSIONS | 40 | 15 | 7 | 18 | 37.5% | 0.820 | 5693 ms |
| EQUATIONS_INEQUALITIES | 40 | 23 | 7 | 10 | 57.5% | 0.858 | 10031 ms |
| POWERS_SUBSCRIPTS_ROOTS | 40 | 16 | 6 | 18 | 40.0% | 0.830 | 28810 ms |
| FRACTIONS_RATIONAL | 40 | 28 | 2 | 10 | 70.0% | 0.846 | 5103 ms |
| COMPLEX_NUMBERS | 40 | 14 | 11 | 15 | 35.0% | 0.842 | 5678 ms |
| LOG_EXP_SPECIAL | 40 | 13 | 5 | 22 | 32.5% | 0.745 | 6334 ms |
| TRIGONOMETRY | 40 | 21 | 4 | 15 | 52.5% | 0.818 | 6037 ms |
| CALCULUS | 40 | 5 | 6 | 29 | 12.5% | 0.711 | 7295 ms |
| MATRICES_VECTORS | 40 | 4 | 10 | 26 | 10.0% | 0.716 | 25763 ms |
| GRAPHS | 40 | 30 | 0 | 10 | 75.0% | 0.911 | 4 ms |
| GEOMETRY_DIAGRAMS | 40 | 30 | 0 | 10 | 75.0% | 0.892 | 6 ms |
| PROBABILITY_STATISTICS | 40 | 2 | 4 | 34 | 5.0% | 0.673 | 28514 ms |
| SETS_LOGIC | 40 | 0 | 5 | 35 | 0.0% | 0.690 | 25783 ms |

## 4. Category analysis

### BASIC_ARITHMETIC

- Strength: basic_arithmetic-040.
- Weakness: basic_arithmetic-033.
- Most frequent error: STROKE_ORDER_SENSITIVE (4 cases).
- Recommended correction: see the evidence-linked recommendation file.

### ALGEBRAIC_EXPRESSIONS

- Strength: algebraic_expressions-003.
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
- Most frequent error: LETTER_CONFUSION (23 cases).
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
- Most frequent error: MATRIX_ROW_ERROR (19 cases).
- Recommended correction: see the evidence-linked recommendation file.

### GRAPHS

- Strength: graphs-029.
- Weakness: graphs-004.
- Most frequent error: GRAPH_TYPE_WRONG (9 cases).
- Recommended correction: see the evidence-linked recommendation file.

### GEOMETRY_DIAGRAMS

- Strength: geometry_diagrams-029.
- Weakness: geometry_diagrams-001.
- Most frequent error: SHAPE_TYPE_WRONG (10 cases).
- Recommended correction: see the evidence-linked recommendation file.

### PROBABILITY_STATISTICS

- Strength: probability_statistics-024.
- Weakness: probability_statistics-011.
- Most frequent error: LETTER_CONFUSION (31 cases).
- Recommended correction: see the evidence-linked recommendation file.

### SETS_LOGIC

- Strength: sets_logic-039.
- Weakness: sets_logic-002.
- Most frequent error: LETTER_CONFUSION (30 cases).
- Recommended correction: see the evidence-linked recommendation file.

## 5. Expected versus detected examples

- **basic_arithmetic-008** — Expected `144/12`; detected `144/12`; normalized `144/12`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-013** — Expected `12+8*4-6/3`; detected `12+8 \times 4-6/3`; normalized `12+8*4-6/3`; confidence `0.8818135`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-014** — Expected `(36+12)/6`; detected `(36+12)/6`; normalized `(36+12)/6`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-018** — Expected `1/2+3/4`; detected `1/2+3/4`; normalized `1/2+3/4`; confidence `0.85151327`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-027** — Expected `8/4/2`; detected `8/4/2`; normalized `8/4/2`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-030** — Expected `5:7=10:14`; detected `5:7=10:14`; normalized `5:7=10:14`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-038** — Expected `48/(3*(2+6))`; detected `48/(3 \times(2+6))`; normalized `48/(3*(2+6))`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **basic_arithmetic-040** — Expected `9-4=6`; detected `9-4=6`; normalized `9-4=6`; confidence `0.99689996`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-003** — Expected `4x^2+3x-9`; detected `4X^{2}+3X-9`; normalized `4X^(2)+3X-9`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-008** — Expected `a^2-b^2=(a-b)(a+b)`; detected `a^2-b^2=(a-b)(a+b)`; normalized `a^2-b^2=(a-b)(a+b)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-011** — Expected `5m^3-2m+1`; detected `5M^{3}-2M+1`; normalized `5M^(3)-2M+1`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-013** — Expected `z^2+2z+1`; detected `z^{2}+2z+1`; normalized `z^(2)+2z+1`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-015** — Expected `b^2-6b+9`; detected `b^2-6b+9`; normalized `b^2-6b+9`; confidence `0.8809886`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-020** — Expected `x^4-5x^2+4`; detected `x^{4}-5x^{2}+4`; normalized `x^(4)-5x^(2)+4`; confidence `0.9165908`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-021** — Expected `(2x-3)^3`; detected `(2X-3)^3`; normalized `(2X-3)^3`; confidence `0.8902363`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-022** — Expected `x^{-2}+x^{-1}`; detected `X^{-2}+X^{-1}`; normalized `X^(-2)+X^(-1)`; confidence `0.9184618`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-024** — Expected `x_1+x_2+x_3`; detected `X_{1}+X_{2}+X_{3}`; normalized `X_1+X_2+X_3`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-026** — Expected `2xy^2-3x^2y`; detected `2XY^{2}-3X^{2}Y`; normalized `2XY^(2)-3X^(2)Y`; confidence `0.9101673`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-027** — Expected `(x^2-y^2)/(x+y)`; detected `(x^2-y^2)/(X+Y)`; normalized `(x^2-y^2)/(X+Y)`; confidence `0.31936`; status **PASS**; errors `LOW_CONFIDENCE`; evidence `rendered-inputs`.
- **algebraic_expressions-031** — Expected `(a/b)^n`; detected `(a/b)^{n}`; normalized `(a/b)^(n)`; confidence `0.8736372`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-032** — Expected `(x+1/x)^2`; detected `(x+1/x)^{2}`; normalized `(x+1/x)^(2)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **algebraic_expressions-035** — Expected `x^2 +  y^2`; detected `x^2+y^2`; normalized `x^2+y^2`; confidence `0.4192`; status **PASS**; errors `LOW_CONFIDENCE`; evidence `rendered-inputs`.
- **algebraic_expressions-036** — Expected `x^2+y^2+2xy`; detected `x^{2}+y^{2}+2xy`; normalized `x^(2)+y^(2)+2xy`; confidence `0.8830568`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-001** — Expected `2x+5=17`; detected `2x+5=17`; normalized `2x+5=17`; confidence `0.95251596`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-002** — Expected `3x-7=2x+8`; detected `3X-7=2X+8`; normalized `3X-7=2X+8`; confidence `0.87535876`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-003** — Expected `x^2-5x+6=0`; detected `x^{2}-5x+6=0`; normalized `x^(2)-5x+6=0`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-004** — Expected `x^3-4x=0`; detected `x^{3}-4x=0`; normalized `x^(3)-4x=0`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-005** — Expected `(x+1)/3=(2x-5)/7`; detected `(x+1)/3=(2x-5)/7`; normalized `(x+1)/3=(2x-5)/7`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-007** — Expected `2<x<=9`; detected `2<x<=9`; normalized `2<x<=9`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-008** — Expected `-3<=2x+1<7`; detected `-3<=2x+1<7`; normalized `-3<=2x+1<7`; confidence `0.95945996`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-012** — Expected `x^2+y^2=25`; detected `x^{2}+y^{2}=25`; normalized `x^(2)+y^(2)=25`; confidence `0.9206679`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-013** — Expected `xy=12,x+y=7`; detected `xy=12,x+y=7`; normalized `xy=12,x+y=7`; confidence `0.8350809`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-015** — Expected `0<=x<=1`; detected `0<=x<=1`; normalized `0<=x<=1`; confidence `0.8526629`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-016** — Expected `x<-2 or x>3`; detected `x<-2orx>3`; normalized `x<-2orx>3`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-017** — Expected `ax+b=0`; detected `ax+b=0`; normalized `ax+b=0`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-019** — Expected `e^x=5`; detected `e^{x}=5`; normalized `e^(x)=5`; confidence `0.8019844`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-020** — Expected `log_2(x)=7`; detected `log_{2}(X)=7`; normalized `log_2(X)=7`; confidence `0.8836788`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-021** — Expected `sin(x)=1/2`; detected `sin(x)=1/2`; normalized `sin(x)=1/2`; confidence `0.97932845`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-022** — Expected `(x-1)(x+2)=0`; detected `(x-1)(x+2)=0`; normalized `(x-1)(x+2)=0`; confidence `0.94857997`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-023** — Expected `1/x+1/(x+1)=1`; detected `1/x+1/(x+1)=1`; normalized `1/x+1/(x+1)=1`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-025** — Expected `x^(2/3)=4`; detected `x^(2/3)=4`; normalized `x^(2/3)=4`; confidence `1.0`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-026** — Expected `3^{2x}=27`; detected `3^{2x}=27`; normalized `3^(2x)=27`; confidence `0.9157435`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-029** — Expected `x/2<=3`; detected `x/2<=3`; normalized `x/2<=3`; confidence `0.96368396`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-031** — Expected `x^2=2x+3`; detected `x^{2}=2x+3`; normalized `x^(2)=2x+3`; confidence `0.9224333`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-032** — Expected `y=mx+c`; detected `Y=MX+C`; normalized `Y=MX+C`; confidence `1.0`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **equations_inequalities-039** — Expected `x^2-1=0 => x=+-1`; detected `x^2-1=0=>X=+-1`; normalized `x^2-1=0=>X=+-1`; confidence `0.587`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-001** — Expected `x^2`; detected `x^2`; normalized `x^2`; confidence `0.9038582`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-002** — Expected `x^3`; detected `x^{3}`; normalized `x^(3)`; confidence `0.8082802`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-003** — Expected `x^n`; detected `x^{n}`; normalized `x^(n)`; confidence `0.9245685`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-005** — Expected `x^{1/2}`; detected `X^{1/2}`; normalized `X^(1/2)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-007** — Expected `(x^2+1)^3`; detected `(X^2+1)^3`; normalized `(X^2+1)^3`; confidence `0.9291293`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-013** — Expected `a_1`; detected `a_{1}`; normalized `a_1`; confidence `0.81119305`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-014** — Expected `x_2`; detected `X_2`; normalized `X_2`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-016** — Expected `x_1^2+x_2^2`; detected `x_1^2+x_2^2`; normalized `x_1^2+x_2^2`; confidence `0.8933441`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-019** — Expected `2^{-3}`; detected `2^{-3}`; normalized `2^(-3)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-024** — Expected `10^{-9}m`; detected `10^{-9}m`; normalized `10^(-9)m`; confidence `0.8723134`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-028** — Expected `x_{i,j}`; detected `X_{i,j}`; normalized `X_i,j`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-030** — Expected `a_0+a_1x+a_2x^2`; detected `a_0+a_1X+a_2X^2`; normalized `a_0+a_1X+a_2X^2`; confidence `0.9219104`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-035** — Expected `2^{x+1}+2^x`; detected `2^{x+1}+2^{x}`; normalized `2^(x+1)+2^(x)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-036** — Expected `x_1^{a+b}`; detected `X_1^{a+b}`; normalized `X_1^(a+b)`; confidence `0.9186638`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-037** — Expected `a_{n+1}^2`; detected `a_{n+1}^2`; normalized `a_n+1^2`; confidence `0.89410573`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **powers_subscripts_roots-039** — Expected `x_ 2`; detected `X_{2}`; normalized `X_2`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-001** — Expected `1/2`; detected `1/2`; normalized `1/2`; confidence `0.9889`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-002** — Expected `3/4`; detected `3/4`; normalized `3/4`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-003** — Expected `2 1/3`; detected `21/3`; normalized `21/3`; confidence `1.0`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-004** — Expected `(x+1)/(x-1)`; detected `(X+1)/(X-1)`; normalized `(X+1)/(X-1)`; confidence `0.944159`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-006** — Expected `1/(1+1/x)`; detected `1/(1+1/x)`; normalized `1/(1+1/x)`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-008** — Expected `(x^2+3x+2)/(x^2-1)`; detected `(X^2+3X+2)/(X^2-1)`; normalized `(X^2+3X+2)/(X^2-1)`; confidence `0.9176005`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-009** — Expected `1+1/(2+1/3)`; detected `1+1/(2+1/3)`; normalized `1+1/(2+1/3)`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-010** — Expected `(1/2)/(3/4)`; detected `(1/2)/(3/4)`; normalized `(1/2)/(3/4)`; confidence `0.88241863`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-011** — Expected `x/(y/z)`; detected `X/(Y/Z)`; normalized `X/(Y/Z)`; confidence `0.9592819`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-014** — Expected `(x+1)/2=3/5`; detected `(x+1)/2=3/5`; normalized `(x+1)/2=3/5`; confidence `0.9713`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-015** — Expected `-3/7`; detected `-3/7`; normalized `-3/7`; confidence `0.94623345`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-016** — Expected `1/-x`; detected `1/-x`; normalized `1/-x`; confidence `0.9022484`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-017** — Expected `(x-y)/(x+y)`; detected `(X-Y)/(X+Y)`; normalized `(X-Y)/(X+Y)`; confidence `0.9841`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-019** — Expected `x^2/(1+x^2)`; detected `X^{2}/(1+X^{2})`; normalized `X^(2)/(1+X^(2))`; confidence `0.9069346`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-020** — Expected `1/(2+1/(3+1/4))`; detected `1/(2+1/(3+1/4))`; normalized `1/(2+1/(3+1/4))`; confidence `0.93865997`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-021** — Expected `a/b+c/d`; detected `a/b+c/d`; normalized `a/b+c/d`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-023** — Expected `1/(x-1)-1/(x+1)`; detected `1/(x-1)-1/(x+1)`; normalized `1/(x-1)-1/(x+1)`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-024** — Expected `(x^3-1)/(x-1)`; detected `(x^{3}-1)/(x-1)`; normalized `(x^(3)-1)/(x-1)`; confidence `0.9140804`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-025** — Expected `(x^2-4)/(x^2+2x)`; detected `(X^2-4)/(X^2+2X)`; normalized `(X^2-4)/(X^2+2X)`; confidence `0.90720415`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-028** — Expected `x^{1/2}/y^{3/2}`; detected `X^{1/2}/Y^{3/2}`; normalized `X^(1/2)/Y^(3/2)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-030** — Expected `(1-r^{n+1})/(1-r)`; detected `(1-r^{n+1})/(1-r)`; normalized `(1-r^(n+1))/(1-r)`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-032** — Expected `(2x^{-2}y^3)/(3x^2y^{-1})`; detected `(2X^{-2}Y^{3})/(3X^{2}Y^{-1})`; normalized `(2X^(-2)Y^(3))/(3X^(2)Y^(-1))`; confidence `0.9291164`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-033** — Expected `((x^2y^{-3})^2)/(x^{-1}y^4)`; detected `((X^{2}Y^{-3})^{2})/(X^{-1}Y^{4})`; normalized `((X^(2)Y^(-3))^(2))/(X^(-1)Y^(4))`; confidence `0.92598563`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-034** — Expected `1 / 2`; detected `1/2`; normalized `1/2`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-036** — Expected `(x+1)//(x-1)`; detected `(x+1)//(x-1)`; normalized `(x+1)//(x-1)`; confidence `0.8808502`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-038** — Expected `1/(x+1)+2`; detected `1/(x+1)+2`; normalized `1/(x+1)+2`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-039** — Expected `(x+1)/(x-1)`; detected `(x+1)/(x-1)`; normalized `(x+1)/(x-1)`; confidence `0.8444354`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **fractions_rational-040** — Expected `(x+1)/(x-1)=0`; detected `(x+1)/(x-1)=0`; normalized `(x+1)/(x-1)=0`; confidence `0.94857997`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-001** — Expected `i^2=-1`; detected `i^2=-1`; normalized `i^2=-1`; confidence `0.9311`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-002** — Expected `z=3+4i`; detected `z=3+4i`; normalized `z=3+4i`; confidence `0.8961`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-008** — Expected `(2+3i)/(1-i)`; detected `(2+3i)/(1-i)`; normalized `(2+3i)/(1-i)`; confidence `0.8909889`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-012** — Expected `z^2+4=0`; detected `z^{2}+4=0`; normalized `z^(2)+4=0`; confidence `0.886184`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-015** — Expected `1/i=-i`; detected `1/i=-i`; normalized `1/i=-i`; confidence `0.9028995`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-016** — Expected `i^3=-i`; detected `i^3=-i`; normalized `i^3=-i`; confidence `0.92820215`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-018** — Expected `(1+i)^2=2i`; detected `(1+i)^{2}=2i`; normalized `(1+i)^(2)=2i`; confidence `0.9268307`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-019** — Expected `z_1+z_2`; detected `z_{1}+z_{2}`; normalized `z_1+z_2`; confidence `0.87448823`; status **PASS**; errors ``; evidence `rendered-inputs`.
- **complex_numbers-020** — Expected `z_1*z_2`; detected `z_1 \times z_2`; normalized `z_1*z_2`; confidence `0.8961`; status **PASS**; errors `LETTER_CONFUSION`; evidence `rendered-inputs`.
- **complex_numbers-023** — Expected `(1+i)(1-i)=2`; detected `(1+i)(1-i)=2`; normalized `(1+i)(1-i)=2`; confidence `0.9713`; status **PASS**; errors ``; evidence `rendered-inputs`.

## 6. Symbol confusion analysis

See `../results/SMART_BOARD_SYMBOL_CONFUSION_MATRIX.csv`; pairs are ranked by observed frequency and impact.

## 7. Layout-recognition analysis

Independent spatial scores cover superscripts, subscripts, fraction bars, root scope, matrices, multiline grouping, graph classification and diagram classification. Failures retain layout errors even where conservative semantic normalization matches.

## 8. Environmental sensitivity

Profiles, regions and stroke variants are recorded per case in the results CSV. Physical stylus/finger and multi-device conclusions require Mode B manual runs and are not inferred from automated replay.

## 9. Performance results

Median 3338 ms; P90 5908 ms; P95 9941 ms; P99 28810 ms. Detailed category metrics are in `../results/SMART_BOARD_PERFORMANCE_METRICS.csv`.

## 10. Root-cause analysis

Observed failures are classified by symbol, spatial hierarchy, grouping, graph/shape classification and environment sensitivity. The raw result is never modified before persistence.

## 11. Prioritized recommendations

See `SMART_BOARD_RECOMMENDATIONS.md` for evidence-linked P0-P4 actions.

## 12. Production-readiness conclusion

**NOT READY** based on the measured automated run. Manual handwriting, stylus/finger, phone/tablet and multi-Android-version coverage remain separate evidence requirements.
