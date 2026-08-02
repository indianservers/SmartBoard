package com.indianservers.smartboard.physics.formulas.data

import com.indianservers.smartboard.physics.formulas.model.*

object BundledPhysicsFormulaData {
    const val SCHEMA_VERSION = 2

    private data class Seed(val id: String, val title: String, val description: String, val topics: List<String>)

    private fun topics(value: String) = value.split(';').map(String::trim)

    private val seeds = listOf(
        Seed("measurements-units", "Measurements, Units and Vectors", "SI units, dimensions, errors and vector basics.", topics("SI Units;Dimensional Analysis;Error Analysis;Significant Figures;Unit Conversions;Vector Components")),
        Seed("kinematics", "Kinematics", "Motion in one, two and circular paths.", topics("Speed and Velocity;Uniform Acceleration;Free Fall;Projectile Motion;Circular Motion;Relative Motion;Motion Graphs")),
        Seed("forces-mechanics", "Forces and Newton's Laws", "Force models, equilibrium and constrained motion.", topics("Newton's Laws;Weight and Gravity;Friction;Inclined Planes;Tension;Springs;Equilibrium")),
        Seed("energy-momentum", "Work, Energy, Power and Momentum", "Energy accounting and conservation laws.", topics("Work;Kinetic Energy;Potential Energy;Power;Efficiency;Linear Momentum;Impulse;Collisions;Centre of Mass")),
        Seed("rotation", "Rotational Motion", "Angular motion, torque, inertia and rolling.", topics("Angular Kinematics;Torque;Moment of Inertia;Angular Momentum;Rotational Energy;Rolling Motion;Static Equilibrium")),
        Seed("gravitation", "Gravitation and Orbits", "Universal gravity, satellites and orbital motion.", topics("Universal Gravitation;Gravitational Field;Gravitational Potential;Orbital Motion;Satellites;Escape Velocity;Kepler's Laws")),
        Seed("matter-fluids", "Properties of Matter and Fluids", "Elasticity, pressure, buoyancy and flow.", topics("Density;Elasticity;Stress and Strain;Pressure;Hydrostatic Pressure;Buoyancy;Continuity Equation;Bernoulli's Principle;Viscosity;Surface Tension")),
        Seed("thermal", "Thermal Physics and Thermodynamics", "Heat, gases, thermodynamic laws and engines.", topics("Temperature Conversion;Thermal Expansion;Specific Heat;Calorimetry;Latent Heat;Heat Transfer;Ideal Gas;Gas Processes;First Law;Entropy;Heat Engines")),
        Seed("oscillations-waves", "Oscillations, Waves and Sound", "SHM, travelling waves, resonance and acoustics.", topics("Simple Harmonic Motion;Pendulum;Wave Equation;Superposition;Standing Waves;Beats;Doppler Effect;Sound Intensity;Resonance")),
        Seed("optics", "Optics", "Ray optics, lenses, mirrors and wave optics.", topics("Reflection;Refraction;Mirrors;Lenses;Prism;Optical Instruments;Interference;Young's Double Slit;Diffraction;Polarisation")),
        Seed("electricity", "Electrostatics and Current Electricity", "Charges, fields, capacitors and DC circuits.", topics("Coulomb's Law;Electric Field;Electric Potential;Gauss's Law;Capacitance;Electric Current;Ohm's Law;Electrical Power;Series and Parallel Circuits;Kirchhoff's Laws")),
        Seed("magnetism-emi", "Magnetism, EMI and AC", "Magnetic force, induction and alternating current.", topics("Magnetic Force;Motion in Magnetic Field;Magnetic Field Sources;Magnetic Dipole;Faraday's Law;Lenz's Law;Inductance;Transformers;RMS Values;AC Impedance")),
        Seed("modern-nuclear-electronics", "Modern, Nuclear and Electronics", "Quantum, atomic, nuclear and semiconductor formulae.", topics("Photon Energy;Photoelectric Effect;Matter Waves;Bohr Model;Mass Energy;Radioactive Decay;Half Life;Binding Energy;Semiconductors;Transistors;Logic Gates")),
        Seed("advanced-tools", "Advanced Mathematical Tools", "Higher-level physics notation and modelling tools.", topics("Vector Calculus;Lagrangian Mechanics;Hamiltonian Mechanics;Electrodynamics;Quantum Operators;Statistical Mechanics;Relativity"))
    )

    val catalogue: PhysicsFormulaCatalogue by lazy {
        val subcategories = seeds.flatMap { seed ->
            seed.topics.map { title ->
                PhysicsFormulaSubcategory(
                    id = "${seed.id}-${slug(title)}",
                    categoryId = seed.id,
                    title = title,
                    description = "Formula relationships, notation and validity conditions for $title."
                )
            }
        }
        val categories = seeds.map { seed ->
            PhysicsFormulaCategory(
                id = seed.id,
                title = seed.title,
                description = seed.description,
                subcategoryIds = subcategories.filter { it.categoryId == seed.id }.map { it.id }
            )
        }
        PhysicsFormulaCatalogue(SCHEMA_VERSION, categories, subcategories, formulas(subcategories))
    }

    private fun formulas(subcategories: List<PhysicsFormulaSubcategory>): List<PhysicsFormula> {
        fun f(
            id: String,
            category: String,
            subcategory: String,
            title: String,
            equation: String,
            spoken: String,
            level: PhysicsFormulaLevel,
            vars: String,
            example: String,
            calculator: Boolean = true,
            derivation: Boolean = true,
            keywords: String = title
        ): PhysicsFormula {
            val subId = subcategories.first { it.categoryId == category && it.title == subcategory }.id
            val variables = vars.split(';').filter(String::isNotBlank).map { spec ->
                spec.split('|').let {
                    PhysicsFormulaVariable(it[0], it[1], it[2], it[2], it.getOrNull(3)?.ifBlank { null }, it.getOrNull(4)?.ifBlank { null })
                }
            }
            val searchable = equation
                .replace("\\frac", "")
                .replace("\\sqrt", "sqrt")
                .replace("\\Delta", "delta")
                .replace("\\theta", "theta")
                .replace("\\lambda", "lambda")
                .replace("\\pi", "pi")
                .replace("\\phi", "phi")
            return PhysicsFormula(
                id = id,
                categoryId = category,
                subcategoryId = subId,
                title = title,
                equation = equation,
                searchableEquation = searchable,
                spokenEquation = spoken,
                description = "Use this LaTeX-style relationship within its stated physical assumptions and with consistent units.",
                minimumLevel = level,
                variables = variables,
                assumptions = listOf("Quantities use a consistent reference frame and SI units unless stated otherwise."),
                limitations = listOf("The relationship applies only under the model conditions shown."),
                alternativeForms = emptyList(),
                derivationSteps = if (derivation) listOf(PhysicsDerivationStep(equation, "This form follows from the defining relationship and algebraic isolation of the required quantity.")) else emptyList(),
                workedExamples = listOf(PhysicsWorkedExample(example, "Substitute the known values in SI units.", "Evaluate and report the requested quantity with its unit.", "Both sides reduce to compatible SI dimensions.")),
                unitCheck = "The left and right sides have matching SI dimensions.",
                calculator = if (calculator) PhysicsCalculatorDefinition(variables.map { it.id }.toSet(), variables.map { it.id }.toSet()) else null,
                relatedFormulaIds = emptyList(),
                relatedConceptIds = emptyList(),
                keywords = (keywords.lowercase().split(Regex("[^a-z0-9]+")) + title.lowercase().split(' ')).filter { it.isNotBlank() }.toSet(),
                featured = level.rank <= PhysicsFormulaLevel.Class10.rank
            )
        }

        return listOf(
            f("physics-percentage-error", "measurements-units", "Error Analysis", "Percentage error", "\\%\\ error = \\frac{|x_m - x_a|}{|x_a|}\\times 100\\%", "percentage error equals absolute difference divided by accepted value times one hundred percent", PhysicsFormulaLevel.Class9, "measured|x_m|measured value||;accepted|x_a|accepted value||", "Compare a 9.8 m measurement with an accepted 10.0 m value.", keywords = "error uncertainty measurement"),
            f("physics-speed", "kinematics", "Speed and Velocity", "Average speed", "v = \\frac{d}{t}", "speed equals distance divided by time", PhysicsFormulaLevel.Class7, "speed|v|speed|m/s|[L T^-1];distance|d|distance|m|[L];time|t|time|s|[T]", "A runner covers 100 m in 20 s.", keywords = "speed velocity distance time"),
            f("physics-final-velocity", "kinematics", "Uniform Acceleration", "Final velocity", "v = u + at", "final velocity equals initial velocity plus acceleration multiplied by time", PhysicsFormulaLevel.Class9, "finalVelocity|v|final velocity|m/s|[L T^-1];initialVelocity|u|initial velocity|m/s|[L T^-1];acceleration|a|acceleration|m/s^2|[L T^-2];time|t|time|s|[T]", "A body starts at 2 m/s and accelerates at 3 m/s^2 for 4 s.", keywords = "kinematic acceleration final velocity"),
            f("physics-newton-second-law", "forces-mechanics", "Newton's Laws", "Newton's second law", "F = ma", "net force equals mass multiplied by acceleration", PhysicsFormulaLevel.Class8, "force|F|net force|N|[M L T^-2];mass|m|mass|kg|[M];acceleration|a|acceleration|m/s^2|[L T^-2]", "Find the force accelerating 5 kg at 2 m/s^2.", keywords = "force newton second law acceleration"),
            f("physics-kinetic-energy", "energy-momentum", "Kinetic Energy", "Kinetic energy", "E_k = \\frac{1}{2}mv^2", "kinetic energy equals one half mass multiplied by speed squared", PhysicsFormulaLevel.Class9, "energy|E_k|kinetic energy|J|[M L^2 T^-2];mass|m|mass|kg|[M];speed|v|speed|m/s|[L T^-1]", "Find the kinetic energy of 2 kg moving at 3 m/s.", keywords = "energy work kinetic"),
            f("physics-momentum", "energy-momentum", "Linear Momentum", "Linear momentum", "p = mv", "momentum equals mass multiplied by velocity", PhysicsFormulaLevel.Class9, "momentum|p|momentum|kg m/s|[M L T^-1];mass|m|mass|kg|[M];velocity|v|velocity|m/s|[L T^-1]", "Find the momentum of 4 kg moving at 5 m/s."),
            f("physics-torque", "rotation", "Torque", "Torque magnitude", "\\tau = rF\\sin\\theta", "torque equals radius times force times sine of the angle", PhysicsFormulaLevel.Class11, "torque|tau|torque|N m|[M L^2 T^-2];radius|r|lever arm|m|[L];force|F|force|N|[M L T^-2];angle|theta|angle|rad|1", "A 10 N force acts 0.5 m from a pivot at right angles."),
            f("physics-gravitation", "gravitation", "Universal Gravitation", "Newton's law of gravitation", "F = G\\frac{m_1m_2}{r^2}", "force equals the gravitational constant times both masses divided by separation squared", PhysicsFormulaLevel.Class11, "force|F|gravitational force|N|[M L T^-2];mass1|m_1|first mass|kg|[M];mass2|m_2|second mass|kg|[M];radius|r|centre separation|m|[L]", "Find the attraction between two known masses separated by a known distance."),
            f("physics-density", "matter-fluids", "Density", "Density", "\\rho = \\frac{m}{V}", "density equals mass divided by volume", PhysicsFormulaLevel.Class8, "density|rho|density|kg/m^3|[M L^-3];mass|m|mass|kg|[M];volume|V|volume|m^3|[L^3]", "A 2 kg sample occupies 0.001 m^3."),
            f("physics-pressure", "matter-fluids", "Pressure", "Pressure", "p = \\frac{F}{A}", "pressure equals normal force divided by area", PhysicsFormulaLevel.Class8, "pressure|p|pressure|Pa|[M L^-1 T^-2];force|F|normal force|N|[M L T^-2];area|A|area|m^2|[L^2]", "A 200 N force acts over 0.5 m^2."),
            f("physics-pendulum", "oscillations-waves", "Pendulum", "Simple pendulum period", "T = 2\\pi\\sqrt{\\frac{L}{g}}", "period equals two pi times the square root of length divided by gravitational acceleration", PhysicsFormulaLevel.Class11, "period|T|period|s|[T];length|L|pendulum length|m|[L];gravity|g|gravitational acceleration|m/s^2|[L T^-2]", "Estimate the period of a 1 m pendulum for small oscillations."),
            f("physics-wave-speed", "oscillations-waves", "Wave Equation", "Wave speed", "v = f\\lambda", "wave speed equals frequency multiplied by wavelength", PhysicsFormulaLevel.Class9, "speed|v|wave speed|m/s|[L T^-1];frequency|f|frequency|Hz|[T^-1];wavelength|lambda|wavelength|m|[L]", "A 5 Hz wave has wavelength 2 m.", keywords = "wave speed frequency wavelength"),
            f("physics-decibel", "oscillations-waves", "Sound Intensity", "Sound intensity level", "\\beta = 10\\log_{10}\\left(\\frac{I}{I_0}\\right)", "sound level equals ten times the base ten logarithm of intensity ratio", PhysicsFormulaLevel.Class11, "level|beta|sound level|dB|1;intensity|I|sound intensity|W/m^2|[M T^-3];reference|I_0|reference intensity|W/m^2|[M T^-3]", "Compare an intensity with the standard reference intensity."),
            f("physics-heat", "thermal", "Specific Heat", "Sensible heat", "Q = mc\\Delta T", "heat equals mass times specific heat capacity times temperature change", PhysicsFormulaLevel.Class9, "heat|Q|heat transferred|J|[M L^2 T^-2];mass|m|mass|kg|[M];capacity|c|specific heat capacity|J/(kg K)|[L^2 T^-2 K^-1];temperature|Delta T|temperature change|K|[K]", "Heat 1 kg of water through 10 K."),
            f("physics-coulomb", "electricity", "Coulomb's Law", "Coulomb's law", "F = k\\frac{|q_1q_2|}{r^2}", "force magnitude equals Coulomb constant times charge product divided by separation squared", PhysicsFormulaLevel.Class11, "force|F|electric force|N|[M L T^-2];charge1|q_1|first charge|C|[I T];charge2|q_2|second charge|C|[I T];radius|r|separation|m|[L]", "Find the force between two point charges."),
            f("physics-ohm", "electricity", "Ohm's Law", "Ohm's law", "V = IR", "voltage equals current multiplied by resistance", PhysicsFormulaLevel.Class10, "voltage|V|potential difference|V|[M L^2 T^-3 I^-1];current|I|current|A|[I];resistance|R|resistance|ohm|[M L^2 T^-3 I^-2]", "A 2 A current passes through 5 ohm.", keywords = "ohm voltage current resistance electricity"),
            f("physics-magnetic-force", "magnetism-emi", "Magnetic Force", "Magnetic force magnitude", "F = qvB\\sin\\theta", "force equals charge times speed times magnetic field times sine of angle", PhysicsFormulaLevel.Class12, "force|F|magnetic force|N|[M L T^-2];charge|q|charge|C|[I T];speed|v|speed|m/s|[L T^-1];field|B|magnetic field|T|[M T^-2 I^-1];angle|theta|angle|rad|1", "A charge moves through a uniform magnetic field."),
            f("physics-faraday", "magnetism-emi", "Faraday's Law", "Faraday's law", "\\varepsilon = -N\\frac{\\Delta\\Phi}{\\Delta t}", "induced emf equals negative turns times rate of change of magnetic flux", PhysicsFormulaLevel.Class12, "emf|epsilon|induced emf|V|[M L^2 T^-3 I^-1];turns|N|number of turns||1;flux|Delta Phi|flux change|Wb|[M L^2 T^-2 I^-1];time|Delta t|time interval|s|[T]", "A coil experiences a known flux change in a known time."),
            f("physics-ac-rms", "magnetism-emi", "RMS Values", "Sinusoidal RMS voltage", "V_{rms} = \\frac{V_0}{\\sqrt{2}}", "root mean square voltage equals peak voltage divided by square root of two", PhysicsFormulaLevel.Class12, "rms|V_rms|RMS voltage|V|[M L^2 T^-3 I^-1];peak|V_0|peak voltage|V|[M L^2 T^-3 I^-1]", "Convert 325 V peak sinusoidal voltage to RMS."),
            f("physics-photon", "modern-nuclear-electronics", "Photon Energy", "Photon energy", "E = hf", "photon energy equals Planck constant multiplied by frequency", PhysicsFormulaLevel.Class12, "energy|E|photon energy|J|[M L^2 T^-2];frequency|f|frequency|Hz|[T^-1]", "Find photon energy for a known frequency."),
            f("physics-thin-lens", "optics", "Lenses", "Thin-lens equation", "\\frac{1}{f} = \\frac{1}{v} - \\frac{1}{u}", "inverse focal length equals inverse image distance minus inverse object distance under Cartesian sign convention", PhysicsFormulaLevel.Class10, "focal|f|focal length|m|[L];image|v|image distance|m|[L];object|u|object distance|m|[L]", "Find image distance from object distance and focal length.", keywords = "lens equation optics focal image"),
            f("physics-fringe-width", "optics", "Young's Double Slit", "Fringe width", "\\beta = \\frac{\\lambda D}{d}", "fringe width equals wavelength times screen distance divided by slit separation", PhysicsFormulaLevel.Class12, "width|beta|fringe width|m|[L];wavelength|lambda|wavelength|m|[L];distance|D|screen distance|m|[L];separation|d|slit separation|m|[L]", "Calculate fringe spacing for a double-slit setup."),
            f("physics-photoelectric", "modern-nuclear-electronics", "Photoelectric Effect", "Einstein photoelectric equation", "K_{max} = hf - \\phi", "maximum kinetic energy equals photon energy minus work function", PhysicsFormulaLevel.Class12, "energy|K_max|maximum kinetic energy|J|[M L^2 T^-2];frequency|f|frequency|Hz|[T^-1];work|phi|work function|J|[M L^2 T^-2]", "Find emitted-electron maximum energy above threshold."),
            f("physics-radioactive-decay", "modern-nuclear-electronics", "Radioactive Decay", "Radioactive decay law", "N = N_0e^{-\\lambda t}", "remaining nuclei equal initial nuclei times exponential negative decay constant times time", PhysicsFormulaLevel.Class12, "remaining|N|remaining nuclei||1;initial|N_0|initial nuclei||1;constant|lambda|decay constant|s^-1|[T^-1];time|t|elapsed time|s|[T]", "Find the remaining fraction after a known time."),
            f("physics-transistor-current", "modern-nuclear-electronics", "Transistors", "Transistor current relation", "I_E = I_B + I_C", "emitter current equals base current plus collector current", PhysicsFormulaLevel.Class12, "emitter|I_E|emitter current|A|[I];base|I_B|base current|A|[I];collector|I_C|collector current|A|[I]", "Find emitter current from base and collector currents."),
            f("physics-lagrangian", "advanced-tools", "Lagrangian Mechanics", "Euler-Lagrange equation", "\\frac{d}{dt}\\left(\\frac{\\partial L}{\\partial \\dot{q}}\\right) - \\frac{\\partial L}{\\partial q} = 0", "time derivative of partial L by partial q dot minus partial L by partial q equals zero", PhysicsFormulaLevel.Undergraduate, "lagrangian|L|Lagrangian|J|[M L^2 T^-2];coordinate|q|generalised coordinate||;time|t|time|s|[T]", "Apply the equation to a one-dimensional conservative system.", calculator = false, keywords = "lagrangian advanced mechanics calculus")
        )
    }

    private fun slug(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
