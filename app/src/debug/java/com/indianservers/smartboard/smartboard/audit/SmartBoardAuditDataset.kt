package com.indianservers.smartboard.smartboard.audit

object SmartBoardAuditDataset {
    private data class Seed(
        val value: String,
        val subcategory: String,
        val structure: String? = null,
        val tags: Set<String> = emptySet(),
    )

    val cases: List<SmartBoardAuditCase> by lazy {
        val all = buildList {
            addText(AuditCategory.BASIC_ARITHMETIC, arithmetic())
            addText(AuditCategory.ALGEBRAIC_EXPRESSIONS, algebra())
            addText(AuditCategory.EQUATIONS_INEQUALITIES, equations())
            addText(AuditCategory.POWERS_SUBSCRIPTS_ROOTS, powers())
            addText(AuditCategory.FRACTIONS_RATIONAL, fractions())
            addText(AuditCategory.COMPLEX_NUMBERS, complex())
            addText(AuditCategory.LOG_EXP_SPECIAL, logs())
            addText(AuditCategory.TRIGONOMETRY, trigonometry())
            addText(AuditCategory.CALCULUS, calculus())
            addText(AuditCategory.MATRICES_VECTORS, matrices())
            addGraphs(graphs())
            addGeometry(geometry())
            addText(AuditCategory.PROBABILITY_STATISTICS, probability())
            addText(AuditCategory.SETS_LOGIC, sets())
        }
        require(all.size == 560) { "Audit corpus must contain exactly 560 cases, got ${all.size}" }
        require(all.map { it.id }.distinct().size == all.size)
        AuditCategory.entries.forEach { category ->
            require(all.count { it.category == category } == 40) { "$category must contain 40 cases" }
        }
        all
    }

    private fun MutableList<SmartBoardAuditCase>.addText(category: AuditCategory, seeds: List<Seed>) {
        require(seeds.size == 40)
        seeds.forEachIndexed { index, seed ->
            add(case(category, index, seed.value, seed.subcategory, seed.structure, seed.tags))
        }
    }

    private fun MutableList<SmartBoardAuditCase>.addGraphs(seeds: List<Seed>) {
        require(seeds.size == 40)
        seeds.forEachIndexed { index, seed ->
            val fields = seed.value.split('|')
            add(
                case(
                    AuditCategory.GRAPHS,
                    index,
                    fields[0],
                    seed.subcategory,
                    "graph:${fields[1]}",
                    seed.tags + "actual-graph",
                ).copy(
                    expectedGraph = ExpectedGraph(
                        type = fields[1],
                        equation = fields[0],
                        keyPoints = fields.drop(2),
                    ),
                ),
            )
        }
    }

    private fun MutableList<SmartBoardAuditCase>.addGeometry(seeds: List<Seed>) {
        require(seeds.size == 40)
        seeds.forEachIndexed { index, seed ->
            val fields = seed.value.split('|')
            add(
                case(
                    AuditCategory.GEOMETRY_DIAGRAMS,
                    index,
                    fields[0],
                    seed.subcategory,
                    "shape:${fields[1]}",
                    seed.tags + "actual-diagram",
                ).copy(
                    expectedDiagram = ExpectedDiagram(
                        shapeType = fields[1],
                        labels = fields.drop(2),
                        relationships = seed.tags.filter { it.startsWith("relation:") },
                    ),
                ),
            )
        }
    }

    private fun case(
        category: AuditCategory,
        index: Int,
        value: String,
        subcategory: String,
        structure: String?,
        tags: Set<String>,
    ): SmartBoardAuditCase {
        val number = index + 1
        val difficulty = when (index) {
            in 0..9 -> AuditDifficulty.EASY
            in 10..21 -> AuditDifficulty.MEDIUM
            in 22..33 -> AuditDifficulty.HARD
            else -> AuditDifficulty.EXTREME
        }
        val profiles = HandwritingProfile.entries
        val regions = CanvasRegion.entries
        return SmartBoardAuditCase(
            id = "${category.name.lowercase()}-${number.toString().padStart(3, '0')}",
            category = category,
            subcategory = subcategory,
            difficulty = difficulty,
            expectedPlainText = value.substringBefore('|'),
            expectedLatex = value.substringBefore('|'),
            expectedStructure = structure ?: inferStructure(value),
            expectedGraph = null,
            expectedDiagram = null,
            handwritingProfile = profiles[index % profiles.size],
            strokeVariant = when (index % 8) {
                0 -> "normal-order"
                1 -> "reverse-symbol-strokes"
                2 -> "delayed-superscript"
                3 -> "fraction-bar-first"
                4 -> "fraction-bar-last"
                5 -> "uneven-spacing"
                6 -> "tilted-baseline"
                else -> "overlap-and-correction"
            },
            canvasRegion = regions[index % regions.size],
            tags = tags + difficulty.name.lowercase(),
        )
    }

    private fun inferStructure(value: String): String = buildList {
        if ('=' in value) add("equation")
        if ('<' in value || '>' in value || "≤" in value || "≥" in value) add("inequality")
        if ('/' in value || "\\frac" in value) add("fraction")
        if ('^' in value) add("superscript")
        if ('_' in value) add("subscript")
        if ("sqrt" in value || '√' in value || '∛' in value) add("root")
        if ('\n' in value || ';' in value) add("multiline")
        if ("[[" in value || "matrix" in value) add("matrix")
        if ("int" in value || '∫' in value) add("integral")
        if ("sum" in value || 'Σ' in value) add("summation")
    }.joinToString("+").ifBlank { "linear-symbol-sequence" }

    private fun s(value: String, subcategory: String, structure: String? = null, vararg tags: String) =
        Seed(value, subcategory, structure, tags.toSet())

    private fun arithmetic() = listOf(
        s("7", "single digit"), s("108", "multi-digit"), s("-42", "negative"),
        s("3.14159", "decimal"), s("12+8", "addition"), s("95-47", "subtraction"),
        s("13*9", "multiplication"), s("144/12", "division"), s("8(4+3)", "implicit multiplication"),
        s("25%", "percentage"), s("3:5", "ratio"), s("18-(-7)", "nested negative"),
        s("12+8*4-6/3", "operator precedence"), s("(36+12)/6", "grouped division"),
        s("[14-3]*5", "square brackets"), s("0.006+1.04", "decimal points"),
        s("-7*-8", "signed multiplication"), s("1/2+3/4", "fraction arithmetic"),
        s("100-99+98-97", "repeated operators"), s("2(3+4(5-2))", "nested parentheses"),
        s("9999+1", "large number"), s("6.02*10^23", "scientific arithmetic", "superscript"),
        s("0/7", "zero division numerator"), s("7.0-0.07", "decimal alignment"),
        s("1-2-3-4", "operator associativity"), s("2--3", "repeated minus"),
        s("8/4/2", "repeated division"), s("(1+2)(3+4)", "adjacent groups"),
        s("12.5%", "decimal percentage"), s("5:7=10:14", "ratio equation"),
        s("1,024+2,048", "thousands separators"), s("0.333...", "repeating decimal"),
        s("|-17|", "absolute number"), s("3!+4!", "factorial arithmetic"),
        s("2^10", "numeric power"), s("sqrt(144)", "numeric root"),
        s("1+2+3+4+5+6+7+8", "long sum"), s("48/(3*(2+6))", "deep grouping"),
        s("7+?=12", "missing operand"), s("9-4=6", "incorrect written equality"),
    )

    private fun algebra() = listOf(
        s("3x+5", "linear"), s("7a-2b", "two variables"), s("4x^2+3x-9", "quadratic"),
        s("(x+2)(x-5)", "factorized"), s("a(b+c)-d", "distribution"), s("|2x-7|", "absolute value"),
        s("(x+y)^2", "binomial power"), s("a^2-b^2=(a-b)(a+b)", "identity"),
        s("2[x-3(y+1)]", "nested brackets"), s("xy+yz+zx", "implicit products"),
        s("5m^3-2m+1", "cubic"), s("p(q-r)+s", "letter ambiguity"),
        s("z^2+2z+1", "z versus two"), s("l^2+1", "l versus one"),
        s("b^2-6b+9", "b versus six"), s("q^2-9q", "q versus nine"),
        s("S_5+5S", "S versus five"), s("x*x+x", "x multiplication ambiguity"),
        s("(a+(b-c))^2", "nested parentheses"), s("x^4-5x^2+4", "quartic"),
        s("(2x-3)^3", "powered factor"), s("x^{-2}+x^{-1}", "negative powers"),
        s("a^{m+n}", "symbolic power"), s("x_1+x_2+x_3", "indexed terms"),
        s("a_n=a_1+(n-1)d", "sequence"), s("2xy^2-3x^2y", "mixed powers"),
        s("(x^2-y^2)/(x+y)", "rational identity"), s("max(a,b)-min(a,b)", "named functions"),
        s("{x+1,x<0;x^2,x>=0}", "piecewise"), s("||x|-1|", "nested absolute"),
        s("(a/b)^n", "fractional base power"), s("(x+1/x)^2", "nested rational"),
        s("A(B+C)-AB", "mixed case"), s("alpha*x+beta", "Greek names"),
        s("x^2 +  y^2", "wide spacing"), s("x^2+y^2+2xy", "crowded terms"),
        s("(x+3)^2 -> x^2+6x+9", "transformation"), s("x+~~3~~4", "overwritten correction"),
        s("2x+3y\n-4x+y", "multiline expression"), s("((((x+1))))", "redundant grouping"),
    )

    private fun equations() = listOf(
        s("2x+5=17", "linear equation"), s("3x-7=2x+8", "two-sided linear"),
        s("x^2-5x+6=0", "quadratic"), s("x^3-4x=0", "cubic"),
        s("(x+1)/3=(2x-5)/7", "fraction equation"), s("sqrt(x+4)=x-2", "radical equation"),
        s("2<x<=9", "double inequality"), s("-3<=2x+1<7", "mixed inequality"),
        s("x+y=10\n2x-y=3", "two-equation system", "multiline+system"),
        s("x+y+z=6\n2x-y+z=3\nx+2y-z=4", "three-equation system", "multiline+system"),
        s("|x-3|=7", "absolute equation"), s("x^2+y^2=25", "implicit equation"),
        s("xy=12,x+y=7", "nonlinear system"), s("x!=4", "not equal"),
        s("0<=x<=1", "closed interval"), s("x<-2 or x>3", "disjoint inequality"),
        s("ax+b=0", "parameterized linear"), s("x^4-16=0", "quartic"),
        s("e^x=5", "exponential equation"), s("log_2(x)=7", "log equation"),
        s("sin(x)=1/2", "trig equation"), s("(x-1)(x+2)=0", "factored equation"),
        s("1/x+1/(x+1)=1", "rational equation"), s("sqrt(x)+sqrt(x-1)=3", "two roots"),
        s("x^(2/3)=4", "fractional power equation"), s("3^{2x}=27", "nested exponent"),
        s("x+y=5\nx-y=1\nx=3", "redundant system"), s("{x+y=4;2x-y=5}", "braced system"),
        s("x/2<=3", "fraction inequality"), s("(x-1)^2>=0", "quadratic inequality"),
        s("x^2=2x+3", "nonstandard quadratic"), s("y=mx+c", "line equation"),
        s("F=ma", "physics-style equation"), s("a/b=c/d", "proportion"),
        s("x+1=2\nx=1", "working steps"), s("2x+2=10\n2x=8\nx=4", "three working steps"),
        s("x+5=12\nx=12-5\nx=7", "transposition steps"), s("x+5=12\nx=12+5\nx=17", "incorrect working"),
        s("x^2-1=0 => x=+-1", "solution notation"), s("x+y=3\n  x-y=1", "misaligned system"),
    )

    private fun powers() = listOf(
        s("x^2", "square"), s("x^3", "cube"), s("x^n", "symbolic power"),
        s("x^{-2}", "negative power"), s("x^{1/2}", "fractional power"),
        s("a^{b+c}", "compound exponent"), s("(x^2+1)^3", "nested power"),
        s("sqrt(x)", "square root"), s("sqrt(x^2+y^2)", "root scope"),
        s("root_3(27)", "cube root"), s("root_4(16)", "nth root"),
        s("6.02*10^23", "scientific notation"), s("a_1", "single subscript"),
        s("x_2", "numeric subscript"), s("a_{n+1}", "compound subscript"),
        s("x_1^2+x_2^2", "power and subscript"), s("x^{y^2}", "nested superscript"),
        s("(a^m)^n", "power of power"), s("2^{-3}", "negative numeric exponent"),
        s("9^{3/2}", "fraction exponent"), s("sqrt[3](x+1)", "indexed radical"),
        s("sqrt(sqrt(x))", "nested roots"), s("sqrt(x+sqrt(y))", "root within root"),
        s("10^{-9}m", "scientific unit"), s("1.5*10^{-4}", "decimal scientific"),
        s("H_2O", "chemical-style subscript"), s("CO_2", "chemical indexed notation"),
        s("x_{i,j}", "two-dimensional index"), s("T_i^{jk}", "tensor-like notation"),
        s("a_0+a_1x+a_2x^2", "indexed polynomial"), s("x^{2^3}", "power tower"),
        s("x^2^3", "ambiguous power tower"), s("sqrt(x^2+1)/(x_1)", "mixed layout"),
        s("e^{-x^2}", "compound negative exponent"), s("2^{x+1}+2^x", "adjacent powers"),
        s("x_1^{a+b}", "compound index and power"), s("a_{n+1}^2", "delayed index"),
        s("x^ 2", "detached superscript"), s("x_ 2", "detached subscript"),
        s("x^2+x_2", "crowded super/subscripts"),
    )

    private fun fractions() = listOf(
        s("1/2", "simple fraction"), s("3/4", "simple fraction"), s("2 1/3", "mixed number"),
        s("(x+1)/(x-1)", "algebraic fraction"), s("(a^2-b^2)/(a+b)", "powered fraction"),
        s("1/(1+1/x)", "nested fraction"), s("sqrt(x)/(x+2)", "root fraction"),
        s("(x^2+3x+2)/(x^2-1)", "polynomial fraction"), s("1+1/(2+1/3)", "continued fraction"),
        s("(1/2)/(3/4)", "fraction over fraction"), s("x/(y/z)", "nested denominator"),
        s("(a/b)/c", "nested numerator"), s("dy/dx", "derivative fraction"),
        s("(x+1)/2=3/5", "fraction equation"), s("-3/7", "negative fraction"),
        s("1/-x", "negative denominator"), s("(x-y)/(x+y)", "similar numerator denominator"),
        s("(1+sqrt(5))/2", "root numerator"), s("x^2/(1+x^2)", "power fraction"),
        s("1/(2+1/(3+1/4))", "deep continued fraction"), s("a/b+c/d", "two fractions"),
        s("(a+b)/(c+d)+(e+f)/(g+h)", "long fraction sum"), s("1/(x-1)-1/(x+1)", "fraction difference"),
        s("(x^3-1)/(x-1)", "factorable rational"), s("(x^2-4)/(x^2+2x)", "complex rational"),
        s("sqrt(x/y)", "root containing fraction"), s("sqrt(x)/sqrt(y)", "two rooted fractions"),
        s("x^{1/2}/y^{3/2}", "fractional powers"), s("(sum_{i=1}^n i)/n", "summation numerator"),
        s("(1-r^{n+1})/(1-r)", "geometric sum"), s("n!/(r!(n-r)!)", "combinatorial fraction"),
        s("(2x^{-2}y^3)/(3x^2y^{-1})", "mixed signed powers"),
        s("((x^2y^{-3})^2)/(x^{-1}y^4)", "deep power fraction"),
        s("1 / 2", "short separated fraction"), s("(x+1)----------------(x-1)", "overlong bar"),
        s("(x+1)//(x-1)", "broken bar"), s("(x+1)\\(x-1)", "slanted reverse bar"),
        s("1/(x+1)+2", "bar written first"), s("(x+1)/(x-1)", "denominator first"),
        s("(x+1)/(x-1)=0", "crowded stacked equation"),
    )

    private fun complex() = listOf(
        s("i^2=-1", "imaginary unit"), s("z=3+4i", "rectangular form"),
        s("conj(z)=3-4i", "conjugate"), s("|3+4i|=5", "modulus"),
        s("z=r(cos(theta)+i*sin(theta))", "polar form"), s("z=r*e^{i*theta}", "Euler form"),
        s("e^{i*pi}+1=0", "Euler identity"), s("(2+3i)/(1-i)", "complex fraction"),
        s("i^{17}", "power of i"), s("Re(z)=3", "real part"),
        s("Im(z)=4", "imaginary part"), s("z^2+4=0", "complex equation"),
        s("arg(z)=theta", "argument"), s("|z|^2=z*conj(z)", "modulus identity"),
        s("1/i=-i", "reciprocal i"), s("i^3=-i", "cube of i"),
        s("i^4=1", "fourth power"), s("(1+i)^2=2i", "powered complex"),
        s("z_1+z_2", "indexed complex"), s("z_1*z_2", "complex product"),
        s("(3+4i)+(2-i)", "complex addition"), s("(3+4i)-(2-i)", "complex subtraction"),
        s("(1+i)(1-i)=2", "conjugate product"), s("sqrt(-9)=3i", "imaginary root"),
        s("z^3=1", "roots of unity"), s("omega=e^{2*pi*i/3}", "root of unity"),
        s("z=5(cos(30)+i*sin(30))", "degree polar"), s("z=2e^{-i*pi/4}", "negative argument"),
        s("Re((2+i)/(1-i))", "nested real part"), s("Im(e^{i*theta})", "Euler imaginary part"),
        s("conj(z_1+z_2)=conj(z_1)+conj(z_2)", "conjugate identity"),
        s("|z_1*z_2|=|z_1||z_2|", "modulus product"), s("z+conj(z)=2Re(z)", "real identity"),
        s("z-conj(z)=2iIm(z)", "imaginary identity"), s("1/(a+bi)", "symbolic reciprocal"),
        s("(a+bi)/(c+di)", "symbolic division"), s("theta=arg(3+4i)", "theta ambiguity"),
        s("i l 1", "i-l-one ambiguity"), s("|1+i|/|1-i|", "bars and fraction"),
        s("z^2+(1-i)z+2+i=0", "complex quadratic"),
    )

    private fun logs() = listOf(
        s("log(x)", "common log"), s("ln(x)", "natural log"), s("log_2(8)=3", "based log"),
        s("log_a(xy)", "arbitrary base"), s("ln(e^x)=x", "log exponential"),
        s("e^{2x+1}", "exponential"), s("2^x=16", "exponential equation"),
        s("log(x+1)-log(x-1)", "log difference"), s("pi*r^2", "pi area"),
        s("2*pi*r", "pi circumference"), s("infinity", "infinity"), s("n!", "factorial"),
        s("floor(x)", "floor"), s("ceil(x)", "ceiling"), s("log_10(1000)=3", "base ten"),
        s("log_b(x^n)=n*log_b(x)", "power identity"), s("log(xy)=log(x)+log(y)", "product identity"),
        s("log(x/y)=log(x)-log(y)", "quotient identity"), s("e^0=1", "zero exponent"),
        s("e^{-x}", "negative exponential"), s("exp(x^2)", "exp function"),
        s("ln(sqrt(x))", "nested log root"), s("log(log(x))", "nested logarithm"),
        s("ln(1+x)-ln(1-x)", "two natural logs"), s("a^x=b", "general exponential"),
        s("log_a(b)=c", "general logarithm"), s("e^{ln(x)}=x", "inverse identity"),
        s("ln(e^{x+y})", "compound exponential"), s("pi~=3.14159", "pi approximation"),
        s("x->infinity", "limit target infinity"), s("(n+1)!", "factorial group"),
        s("n!!", "double factorial"), s("Gamma(n)=(n-1)!", "gamma factorial"),
        s("floor(-1.2)=-2", "negative floor"), s("ceil(-1.2)=-1", "negative ceiling"),
        s("log_2(x)+log_2(x-1)=3", "two based logs"), s("2^x+2^{x+1}=12", "two exponentials"),
        s("e^{i*pi}+1=0", "complex exponential"), s("infinity/8", "infinity-eight confusion"),
        s("lnx", "joined natural log"),
    )

    private fun trigonometry() = listOf(
        s("sin(theta)", "sine"), s("cos(x)", "cosine"), s("tan(45deg)", "tangent degrees"),
        s("sin^2(x)+cos^2(x)=1", "Pythagorean identity"), s("tan(x)=sin(x)/cos(x)", "tangent quotient"),
        s("sec^2(x)-tan^2(x)=1", "secant identity"), s("sin(2x)=2sin(x)cos(x)", "double angle sine"),
        s("cos(2x)=cos^2(x)-sin^2(x)", "double angle cosine"), s("asin(1/2)=30deg", "inverse sine"),
        s("theta=pi/4", "radian angle"), s("A+B+C=180deg", "triangle angles"),
        s("csc(x)=1/sin(x)", "cosecant"), s("sec(x)=1/cos(x)", "secant"),
        s("cot(x)=1/tan(x)", "cotangent"), s("sin(-x)=-sin(x)", "odd identity"),
        s("cos(-x)=cos(x)", "even identity"), s("tan(x+pi)=tan(x)", "periodicity"),
        s("sin(x+pi/2)=cos(x)", "phase identity"), s("cos(x+pi/2)=-sin(x)", "phase cosine"),
        s("sin(a+b)=sin(a)cos(b)+cos(a)sin(b)", "addition formula"),
        s("cos(a+b)=cos(a)cos(b)-sin(a)sin(b)", "cosine addition"),
        s("tan(a+b)=(tan(a)+tan(b))/(1-tan(a)tan(b))", "tangent addition"),
        s("sin(x/2)^2=(1-cos(x))/2", "half angle"), s("cos(x/2)^2=(1+cos(x))/2", "half angle"),
        s("sin(3x)=3sin(x)-4sin^3(x)", "triple angle"), s("cos(3x)=4cos^3(x)-3cos(x)", "triple cosine"),
        s("arctan(1)=pi/4", "inverse tangent"), s("sin^{-1}(x)", "inverse notation"),
        s("csc^2(x)=1+cot^2(x)", "cosecant identity"), s("1+tan^2(x)=sec^2(x)", "tangent identity"),
        s("sin(x)/x", "trig fraction"), s("(1-cos(x))/x^2", "cosine fraction"),
        s("theta_1+theta_2", "indexed Greek angles"), s("alpha+beta=gamma", "Greek angle sum"),
        s("sin^2 theta", "unparenthesized sine power"), s("sin -1 x", "ambiguous inverse"),
        s("2sinxcosx", "crowded function tokens"), s("tanx=1", "joined tangent"),
        s("sin(x)+\ncos(x)", "multiline trigonometry"), s("cos(2x)=1-2sin^2(x)", "alternate double angle"),
    )

    private fun calculus() = listOf(
        s("lim_{x->0} sin(x)/x=1", "limit"), s("dy/dx", "first derivative"),
        s("d^2y/dx^2", "second derivative"), s("partial(z)/partial(x)", "partial derivative"),
        s("f'(x)", "prime derivative"), s("int x^2 dx", "indefinite integral"),
        s("int_0^1 x^2 dx", "definite integral"), s("int int_R f(x,y)dA", "double integral"),
        s("dy/dx+y=e^x", "differential equation"), s("sum_{i=1}^n i", "summation"),
        s("prod_{i=1}^n a_i", "product"), s("nabla dot F", "divergence"),
        s("nabla cross F", "curl"), s("lim_{n->infinity}(1+1/n)^n=e", "sequence limit"),
        s("d/dx(x^n)=nx^{n-1}", "power rule"), s("d/dx sin(x)=cos(x)", "trig derivative"),
        s("d/dx ln(x)=1/x", "log derivative"), s("int cos(x)dx=sin(x)+C", "trig integral"),
        s("int e^x dx=e^x+C", "exponential integral"), s("int 1/x dx=ln|x|+C", "log integral"),
        s("partial^2 f/partial x partial y", "mixed partial"), s("f''(x)+f(x)=0", "ODE"),
        s("y'=xy", "prime ODE"), s("int_a^b f(x)dx", "symbolic limits"),
        s("sum_{k=0}^{infinity}x^k", "infinite series"), s("sum_{n=1}^{infinity}1/n^2", "p-series"),
        s("d/dt(r(t))", "vector derivative"), s("grad f", "gradient"),
        s("nabla^2 phi=0", "Laplacian"), s("oint_C F dot dr", "line integral"),
        s("int int int_V rho dV", "triple integral"), s("d^3y/dx^3", "third derivative"),
        s("lim_{x->a}(f(x)-f(a))/(x-a)", "derivative definition"),
        s("int_0^{pi} sin(x)dx=2", "definite trig integral"),
        s("sum_{i=1}^{n}i^2=n(n+1)(2n+1)/6", "sum identity"),
        s("prod_{k=1}^{n}k=n!", "product identity"), s("int x dx", "integral S ambiguity"),
        s("f .(x)", "prime-dot ambiguity"), s("int_0^1\nx^2 dx", "split integral layout"),
        s("d y / d x", "widely spaced derivative"),
    )

    private fun matrices() = listOf(
        s("[1,2]", "row matrix", "matrix:1x2"), s("[[1],[2],[3]]", "column matrix", "matrix:3x1"),
        s("A=[[1,2],[3,4]]", "square matrix", "matrix:2x2"), s("[[1,2,3],[4,5,6]]", "rectangular matrix", "matrix:2x3"),
        s("det([[1,2],[3,4]])", "determinant", "determinant:2x2"), s("AX=B", "matrix equation"),
        s("A^{-1}", "inverse"), s("A^T", "transpose"), s("I_3", "identity index"),
        s("u dot v", "dot product"), s("u cross v", "cross product"),
        s("v=3i+4j", "vector components"), s("[[-1,2],[3,-4]]", "negative matrix", "matrix:2x2"),
        s("[[1/2,2/3],[3/4,4/5]]", "fraction cells", "matrix:2x2"),
        s("[[x,x^2],[1,y]]", "symbolic matrix", "matrix:2x2"), s("[[1,0],[0,1]]", "identity matrix", "matrix:2x2"),
        s("[[a,b,c]]", "symbolic row", "matrix:1x3"), s("[[x],[y]]=[[2],[3]]", "column equation", "matrix:2x1"),
        s("[[1,2],[3,4]]*[[x],[y]]=[[5],[11]]", "matrix-vector multiplication", "matrix-product"),
        s("[[1,2],[3,4]]+[[5,6],[7,8]]", "matrix addition", "matrix:2x2"),
        s("[[1,2],[3,4]]^2", "matrix power", "matrix:2x2+power"),
        s("det([[x,2],[3,1]])=5", "symbolic determinant", "determinant:2x2"),
        s("[[x,1,-1],[2,x,0],[1,-2,x]]=I", "three by three", "matrix:3x3"),
        s("A^T A=I", "orthogonality"), s("tr(A)=a+d", "trace"),
        s("rank(A)=2", "rank"), s("A_ij", "matrix element"), s("v_i=e_ijk a_j b_k", "indexed vector"),
        s("||v||=sqrt(v dot v)", "vector norm"), s("hat(i) cross hat(j)=hat(k)", "unit vectors"),
        s("[1;2;3] dot [4;5;6]", "column dot product"), s("[[cos(t),-sin(t)],[sin(t),cos(t)]]", "rotation matrix", "matrix:2x2"),
        s("[[a,0,0],[0,b,0],[0,0,c]]", "diagonal matrix", "matrix:3x3"),
        s("[[1,2],[2,4]]", "singular matrix", "matrix:2x2"), s("|1 2;3 4|", "determinant bars", "determinant:2x2"),
        s("[ 1  2 ; 3  4 ]", "wide cells", "matrix:2x2"), s("[[1,2][3,4]]", "missing row separator", "matrix:ambiguous"),
        s("[[1,2],[3,4]", "missing bracket", "matrix:broken"), s("A -1", "detached inverse"),
        s("[[2,1],[3,4]]\n[[x],[y]]\n[[5],[11]]", "vertical matrix equation", "matrix:multiline"),
    )

    private fun graphs() = listOf(
        s("y=x|line|-2,-2;0,0;2,2", "straight line"),
        s("y=2x+1|line|0,1;1,3", "straight line"),
        s("y=-x+3|line|0,3;3,0", "straight line"),
        s("x=2|vertical-line|2,0", "vertical line"),
        s("y=4|horizontal-line|0,4", "horizontal line"),
        s("y=x^2|quadratic|0,0;1,1;-1,1", "parabola"),
        s("y=-x^2+4|quadratic|0,4;-2,0;2,0", "parabola"),
        s("y=(x-2)^2-1|quadratic|2,-1", "shifted parabola"),
        s("y=x^3|cubic|0,0;1,1;-1,-1", "cubic"),
        s("y=abs(x)|absolute|0,0;1,1;-1,1", "absolute value"),
        s("x^2+y^2=9|circle|3,0;0,3", "circle"),
        s("x^2/9+y^2/4=1|ellipse|3,0;0,2", "ellipse"),
        s("x^2/9-y^2/4=1|hyperbola|3,0;-3,0", "hyperbola"),
        s("y=e^x|exponential|0,1", "exponential"),
        s("y=log(x)|logarithmic|1,0", "logarithmic"),
        s("y=sin(x)|sine|0,0;pi/2,1", "sine wave"),
        s("y=cos(x)|cosine|0,1;pi/2,0", "cosine wave"),
        s("y=tan(x)|tangent|0,0", "tangent"),
        s("y={-x,x<0;x,x>=0}|piecewise|0,0", "piecewise"),
        s("y=floor(x)|step|0,0;1,1", "step function"),
        s("scatter:(-2,1),(0,3),(2,2)|scatter|-2,1;0,3;2,2", "scatter"),
        s("y>=x|shaded-inequality|0,0", "shaded inequality"),
        s("y=1/x|reciprocal|1,1;-1,-1", "rational"),
        s("y=1/x^2|reciprocal-squared|1,1;-1,1", "rational"),
        s("y=sqrt(x)|square-root|0,0;1,1;4,2", "root"),
        s("y=root_3(x)|cube-root|0,0;1,1;-1,-1", "root"),
        s("y=2^x|exponential|0,1;1,2", "exponential"),
        s("y=(1/2)^x|exponential-decay|0,1;1,0.5", "exponential"),
        s("y=2sin(x)|sine|0,0;pi/2,2", "scaled sine"),
        s("y=sin(2x)|sine|0,0;pi/4,1", "frequency sine"),
        s("y=sin(x)+1|sine|0,1", "shifted sine"),
        s("y=e^{-x^2}|gaussian|0,1", "Gaussian"),
        s("y=1/(1+e^{-x})|logistic|0,0.5", "logistic"),
        s("y=sign(x)|signum|-1,-1;1,1", "signum"),
        s("y=cosh(x)|hyperbolic|0,1", "hyperbolic"),
        s("r=2+2cos(theta)|polar-cardioid|0,4;pi,0", "polar"),
        s("x=cos(t);y=sin(t)|parametric-circle|1,0;0,1", "parametric"),
        s("y=(x^2-1)/(x+1)|rational-hole|-1,0", "rational"),
        s("y=ln(x)/x|log-rational|1,0", "advanced rational"),
        s("axes+grid+y=x^2|quadratic-grid|0,0", "axes and grid"),
    )

    private fun geometry() = listOf(
        s("point A|POINT|A", "point"), s("line AB|LINE|A|B", "line"),
        s("ray AB|RAY|A|B", "ray"), s("segment AB|LINE_SEGMENT|A|B", "segment"),
        s("parallel lines|PARALLEL_LINES", "relationship", null, "relation:parallel"),
        s("perpendicular lines|PERPENDICULAR_LINES", "relationship", null, "relation:perpendicular"),
        s("angle ABC|ANGLE|A|B|C", "angle"), s("right angle|RIGHT_ANGLE", "angle mark"),
        s("triangle ABC|TRIANGLE|A|B|C", "triangle"), s("right triangle|RIGHT_TRIANGLE", "triangle"),
        s("isosceles triangle|TRIANGLE", "triangle", null, "relation:equal-sides"),
        s("equilateral triangle|EQUILATERAL_TRIANGLE", "triangle", null, "relation:three-equal-sides"),
        s("rectangle|RECTANGLE", "quadrilateral"), s("square|SQUARE", "quadrilateral"),
        s("parallelogram|POLYGON", "quadrilateral", null, "relation:opposite-parallel"),
        s("rhombus|POLYGON", "quadrilateral", null, "relation:equal-sides"),
        s("trapezium|POLYGON", "quadrilateral"), s("pentagon|PENTAGON", "polygon"),
        s("hexagon|HEXAGON", "polygon"), s("octagon|POLYGON", "polygon"),
        s("circle center O|CIRCLE|O", "circle"), s("circle radius OA|CIRCLE|O|A", "circle", null, "relation:radius"),
        s("circle diameter AB|CIRCLE|A|B", "circle", null, "relation:diameter"),
        s("arc AB|ARC|A|B", "circle part"), s("chord AB|CIRCLE|A|B", "circle part", null, "relation:chord"),
        s("tangent at A|CIRCLE|A", "circle part", null, "relation:tangent"),
        s("secant AB|CIRCLE|A|B", "circle part", null, "relation:secant"),
        s("coordinate triangle|TRIANGLE|A|B|C", "coordinate geometry"),
        s("cube|CUBE", "3D shape"), s("cuboid|CUBOID", "3D shape"),
        s("cylinder|CYLINDER", "3D shape"), s("cone|CONE", "3D shape"),
        s("sphere|SPHERE", "3D shape"), s("square pyramid|PYRAMID", "3D shape"),
        s("triangular prism|CUBOID", "3D shape"), s("hemisphere|SPHERE", "3D shape"),
        s("frustum|CONE", "3D shape"), s("labelled cube ABCD|CUBE|A|B|C|D", "labelled 3D"),
        s("triangle 3,4,5|RIGHT_TRIANGLE|3|4|5", "measured triangle"),
        s("cube hidden edges|CUBE", "dashed hidden edges", null, "relation:hidden-edges"),
    )

    private fun probability() = listOf(
        s("mean=sum(x)/n", "mean"), s("median=middle(x)", "median"), s("mode=most(x)", "mode"),
        s("range=max-min", "range"), s("variance=sum((x-mean)^2)/n", "variance"),
        s("sigma=sqrt(variance)", "standard deviation"), s("P(A)", "probability"),
        s("P(A union B)", "union probability"), s("P(A intersect B)", "intersection probability"),
        s("P(A|B)", "conditional probability"), s("P(A^c)", "complement"),
        s("C(n,r)", "combination"), s("P(n,r)", "permutation"), s("(n choose r)", "binomial"),
        s("mu", "population mean"), s("sigma^2", "population variance"),
        s("X~N(mu,sigma^2)", "normal distribution"), s("P(X<=5)", "cumulative probability"),
        s("E(X)=sum xP(x)", "expectation"), s("Var(X)=E(X^2)-E(X)^2", "variance identity"),
        s("P(A union B)=P(A)+P(B)-P(A intersect B)", "addition rule"),
        s("P(A|B)=P(A intersect B)/P(B)", "conditional identity"),
        s("P(A intersect B)=P(A)P(B)", "independence"), s("n!=n(n-1)!", "factorial"),
        s("C(n,r)=n!/(r!(n-r)!)", "combination formula"), s("z=(x-mu)/sigma", "z score"),
        s("xbar=sum_i x_i/n", "sample mean"), s("s^2=sum(x_i-xbar)^2/(n-1)", "sample variance"),
        s("r=cov(X,Y)/(sigma_X sigma_Y)", "correlation"), s("yhat=a+bx", "regression"),
        s("table:[[x,f],[1,3],[2,5]]", "frequency table"), s("bargraph:A=3,B=5,C=2", "bar graph"),
        s("histogram:0-10:4,10-20:7", "histogram"), s("pie:A=40%,B=35%,C=25%", "pie chart"),
        s("boxplot:min,Q1,median,Q3,max", "box plot"), s("N(0,1)", "standard normal"),
        s("P(0<X<1)", "interval probability"), s("P(A|B) != P(B|A)", "conditional distinction"),
        s("P(A union B)", "union-U ambiguity"), s("sigma 6 mu u", "symbol ambiguity"),
    )

    private fun sets() = listOf(
        s("A={1,2,3}", "set literal"), s("x in A", "membership"), s("x notin A", "non-membership"),
        s("A subseteq B", "subset"), s("A subset B", "proper subset"), s("A union B", "union"),
        s("A intersect B", "intersection"), s("A-B", "difference"), s("A^c", "complement"),
        s("emptyset", "empty set"), s("forall x", "universal quantifier"), s("exists x", "existential quantifier"),
        s("p -> q", "implication"), s("p <-> q", "equivalence"), s("p and q", "conjunction"),
        s("p or q", "disjunction"), s("not p", "negation"), s("A subseteq U", "universal set"),
        s("f:A->B", "function mapping"), s("R subseteq A cross B", "relation"),
        s("{x in R:x>0}", "set builder"), s("A symmetric_difference B", "symmetric difference"),
        s("card(A)=n", "cardinality"), s("P(A)", "power set"), s("A intersect emptyset=emptyset", "set identity"),
        s("A union emptyset=A", "set identity"), s("A intersect U=A", "set identity"),
        s("A union U=U", "set identity"), s("(A union B)^c=A^c intersect B^c", "De Morgan"),
        s("(A intersect B)^c=A^c union B^c", "De Morgan"),
        s("venn:A", "Venn diagram", null, "venn"), s("venn:A,B disjoint", "Venn diagram", null, "venn"),
        s("venn:A intersect B", "Venn diagram", null, "venn"), s("venn:A union B", "Venn diagram", null, "venn"),
        s("venn:A subset B", "Venn diagram", null, "venn"), s("venn:A,B,C", "Venn diagram", null, "venn"),
        s("venn:(A union B)^c", "Venn diagram", null, "venn"), s("venn:A-B", "Venn diagram", null, "venn"),
        s("venn:A intersect B intersect C", "Venn diagram", null, "venn"),
        s("truth:[[p,q,p->q],[T,T,T],[T,F,F]]", "truth table"),
    )
}
