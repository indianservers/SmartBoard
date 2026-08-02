package com.indianservers.smartboard.chemistry.data

import com.indianservers.smartboard.chemistry.domain.ElectronConfigurationEngine
import com.indianservers.smartboard.chemistry.model.ChemicalElement
import com.indianservers.smartboard.chemistry.model.DiscoveryInformation
import com.indianservers.smartboard.chemistry.model.ElementCategory
import com.indianservers.smartboard.chemistry.model.Isotope
import com.indianservers.smartboard.chemistry.model.MatterState
import com.indianservers.smartboard.chemistry.model.OccurrenceKind
import com.indianservers.smartboard.chemistry.model.PeriodicBlock
import com.indianservers.smartboard.chemistry.model.PhysicalProperties
import kotlin.math.roundToInt

/** Versioned, offline element source. Optional scientific properties remain null rather than using fake zeroes. */
object BundledElementData {
    const val DATASET_VERSION = "chemistry-elements-core-2026.1"

    private data class Basic(val symbol: String, val name: String, val weight: Double, val displayWeight: String)

    private val basics = """
H|Hydrogen|1.008|1.008
He|Helium|4.0026|4.0026
Li|Lithium|6.94|6.94
Be|Beryllium|9.0122|9.0122
B|Boron|10.81|10.81
C|Carbon|12.011|12.011
N|Nitrogen|14.007|14.007
O|Oxygen|15.999|15.999
F|Fluorine|18.998|18.998
Ne|Neon|20.180|20.180
Na|Sodium|22.990|22.990
Mg|Magnesium|24.305|24.305
Al|Aluminium|26.982|26.982
Si|Silicon|28.085|28.085
P|Phosphorus|30.974|30.974
S|Sulfur|32.06|32.06
Cl|Chlorine|35.45|35.45
Ar|Argon|39.948|39.948
K|Potassium|39.098|39.098
Ca|Calcium|40.078|40.078
Sc|Scandium|44.956|44.956
Ti|Titanium|47.867|47.867
V|Vanadium|50.942|50.942
Cr|Chromium|51.996|51.996
Mn|Manganese|54.938|54.938
Fe|Iron|55.845|55.845
Co|Cobalt|58.933|58.933
Ni|Nickel|58.693|58.693
Cu|Copper|63.546|63.546
Zn|Zinc|65.38|65.38
Ga|Gallium|69.723|69.723
Ge|Germanium|72.630|72.630
As|Arsenic|74.922|74.922
Se|Selenium|78.971|78.971
Br|Bromine|79.904|79.904
Kr|Krypton|83.798|83.798
Rb|Rubidium|85.468|85.468
Sr|Strontium|87.62|87.62
Y|Yttrium|88.906|88.906
Zr|Zirconium|91.224|91.224
Nb|Niobium|92.906|92.906
Mo|Molybdenum|95.95|95.95
Tc|Technetium|98|[98]
Ru|Ruthenium|101.07|101.07
Rh|Rhodium|102.91|102.91
Pd|Palladium|106.42|106.42
Ag|Silver|107.87|107.87
Cd|Cadmium|112.41|112.41
In|Indium|114.82|114.82
Sn|Tin|118.71|118.71
Sb|Antimony|121.76|121.76
Te|Tellurium|127.60|127.60
I|Iodine|126.90|126.90
Xe|Xenon|131.29|131.29
Cs|Caesium|132.91|132.91
Ba|Barium|137.33|137.33
La|Lanthanum|138.91|138.91
Ce|Cerium|140.12|140.12
Pr|Praseodymium|140.91|140.91
Nd|Neodymium|144.24|144.24
Pm|Promethium|145|[145]
Sm|Samarium|150.36|150.36
Eu|Europium|151.96|151.96
Gd|Gadolinium|157.25|157.25
Tb|Terbium|158.93|158.93
Dy|Dysprosium|162.50|162.50
Ho|Holmium|164.93|164.93
Er|Erbium|167.26|167.26
Tm|Thulium|168.93|168.93
Yb|Ytterbium|173.05|173.05
Lu|Lutetium|174.97|174.97
Hf|Hafnium|178.49|178.49
Ta|Tantalum|180.95|180.95
W|Tungsten|183.84|183.84
Re|Rhenium|186.21|186.21
Os|Osmium|190.23|190.23
Ir|Iridium|192.22|192.22
Pt|Platinum|195.08|195.08
Au|Gold|196.97|196.97
Hg|Mercury|200.59|200.59
Tl|Thallium|204.38|204.38
Pb|Lead|207.2|207.2
Bi|Bismuth|208.98|208.98
Po|Polonium|209|[209]
At|Astatine|210|[210]
Rn|Radon|222|[222]
Fr|Francium|223|[223]
Ra|Radium|226|[226]
Ac|Actinium|227|[227]
Th|Thorium|232.04|232.04
Pa|Protactinium|231.04|231.04
U|Uranium|238.03|238.03
Np|Neptunium|237|[237]
Pu|Plutonium|244|[244]
Am|Americium|243|[243]
Cm|Curium|247|[247]
Bk|Berkelium|247|[247]
Cf|Californium|251|[251]
Es|Einsteinium|252|[252]
Fm|Fermium|257|[257]
Md|Mendelevium|258|[258]
No|Nobelium|259|[259]
Lr|Lawrencium|266|[266]
Rf|Rutherfordium|267|[267]
Db|Dubnium|268|[268]
Sg|Seaborgium|269|[269]
Bh|Bohrium|270|[270]
Hs|Hassium|269|[269]
Mt|Meitnerium|278|[278]
Ds|Darmstadtium|281|[281]
Rg|Roentgenium|282|[282]
Cn|Copernicium|285|[285]
Nh|Nihonium|286|[286]
Fl|Flerovium|289|[289]
Mc|Moscovium|290|[290]
Lv|Livermorium|293|[293]
Ts|Tennessine|294|[294]
Og|Oganesson|294|[294]
    """.trimIndent().lineSequence().map { line ->
        val values = line.split('|')
        Basic(values[0], values[1], values[2].toDouble(), values[3])
    }.toList()

    val elements: List<ChemicalElement> by lazy {
        basics.mapIndexed { index, basic -> create(index + 1, basic) }
    }

    private fun create(atomicNumber: Int, basic: Basic): ChemicalElement {
        val group = groupOf(atomicNumber)
        val period = periodOf(atomicNumber)
        val category = categoryOf(atomicNumber)
        val radioactive = atomicNumber == 43 || atomicNumber == 61 || atomicNumber >= 83
        val occurrence = when {
            atomicNumber == 43 || atomicNumber == 61 || atomicNumber >= 93 -> OccurrenceKind.Synthetic
            radioactive -> OccurrenceKind.NaturalTrace
            else -> OccurrenceKind.Natural
        }
        val representativeMass = basic.weight.roundToInt().coerceAtLeast(atomicNumber)
        val configuration = ElectronConfigurationEngine.neutral(atomicNumber)
        return ChemicalElement(
            atomicNumber = atomicNumber,
            symbol = basic.symbol,
            name = basic.name,
            standardAtomicWeight = basic.weight,
            atomicWeightDisplay = basic.displayWeight,
            group = group,
            period = period,
            block = blockOf(atomicNumber, group),
            category = category,
            standardState = stateOf(atomicNumber),
            electronConfiguration = configuration,
            representativeIsotope = Isotope(
                massNumber = representativeMass,
                abundancePercent = null,
                stable = !radioactive,
                note = "Educational representative isotope selected from the conventional atomic-weight value; it is labelled as representative, not treated as an exact standard atomic weight.",
            ),
            valenceElectrons = configuration.outerShellElectrons,
            commonOxidationStates = commonOxidationStates(group),
            physicalProperties = propertiesFor(atomicNumber),
            discovery = DiscoveryInformation(),
            radioactive = radioactive,
            occurrenceKind = occurrence,
            naturalOccurrence = when (occurrence) {
                OccurrenceKind.Natural -> "Occurs naturally on Earth. Abundance varies by element and source."
                OccurrenceKind.NaturalTrace -> "Occurs naturally only in trace quantities through radioactive decay or rare processes."
                OccurrenceKind.Synthetic -> "Primarily produced artificially; any natural occurrence is absent or extremely transient."
            },
            stableIsotopeSummary = if (radioactive) "No stable isotopes; the displayed mass is representative." else "At least one stable isotope; isotope abundances are element-specific.",
            description = "${basic.name} is a ${category.label.lowercase()} in period $period${group?.let { ", group $it" } ?: ", the separated f-block"} of the periodic table.",
        )
    }

    private fun periodOf(z: Int): Int = when (z) { in 1..2 -> 1; in 3..10 -> 2; in 11..18 -> 3; in 19..36 -> 4; in 37..54 -> 5; in 55..86 -> 6; else -> 7 }

    private fun groupOf(z: Int): Int? = when {
        z == 1 -> 1; z == 2 -> 18
        z in 3..10 -> listOf(1, 2, 13, 14, 15, 16, 17, 18)[z - 3]
        z in 11..18 -> listOf(1, 2, 13, 14, 15, 16, 17, 18)[z - 11]
        z in 19..36 -> z - 18
        z in 37..54 -> z - 36
        z == 55 || z == 87 -> 1
        z == 56 || z == 88 -> 2
        z == 57 || z == 89 -> 3
        z in 58..71 || z in 90..103 -> null
        z in 72..86 -> z - 68
        z in 104..118 -> z - 100
        else -> null
    }

    private fun blockOf(z: Int, group: Int?): PeriodicBlock = when {
        z in 58..71 || z in 90..103 -> PeriodicBlock.F
        z == 2 || group in 1..2 -> PeriodicBlock.S
        group in 13..18 -> PeriodicBlock.P
        else -> PeriodicBlock.D
    }

    private fun categoryOf(z: Int): ElementCategory = when {
        z in 109..118 -> ElementCategory.Unknown
        z in 57..71 -> ElementCategory.Lanthanide
        z in 89..103 -> ElementCategory.Actinide
        z in setOf(3, 11, 19, 37, 55, 87) -> ElementCategory.AlkaliMetal
        z in setOf(4, 12, 20, 38, 56, 88) -> ElementCategory.AlkalineEarthMetal
        z in setOf(2, 10, 18, 36, 54, 86) -> ElementCategory.NobleGas
        z in setOf(1, 6, 7, 8, 9, 15, 16, 17, 34, 35, 53) -> ElementCategory.ReactiveNonmetal
        z in setOf(5, 14, 32, 33, 51, 52) -> ElementCategory.Metalloid
        groupOf(z) in 3..12 -> ElementCategory.TransitionMetal
        else -> ElementCategory.PostTransitionMetal
    }

    private fun stateOf(z: Int): MatterState = when {
        z in setOf(1, 2, 7, 8, 9, 10, 17, 18, 36, 54, 86) -> MatterState.Gas
        z == 35 || z == 80 -> MatterState.Liquid
        z >= 104 -> MatterState.Unknown
        else -> MatterState.Solid
    }

    private fun commonOxidationStates(group: Int?): List<Int> = when (group) {
        1 -> listOf(1); 2 -> listOf(2); 13 -> listOf(3); 14 -> listOf(-4, 4)
        15 -> listOf(-3, 3, 5); 16 -> listOf(-2, 4, 6); 17 -> listOf(-1, 1, 5, 7); 18 -> listOf(0)
        else -> emptyList()
    }

    private fun propertiesFor(z: Int): PhysicalProperties {
        val electronegativity = mapOf(
            1 to 2.20, 3 to .98, 4 to 1.57, 5 to 2.04, 6 to 2.55, 7 to 3.04, 8 to 3.44, 9 to 3.98,
            11 to .93, 12 to 1.31, 13 to 1.61, 14 to 1.90, 15 to 2.19, 16 to 2.58, 17 to 3.16,
            19 to .82, 20 to 1.00, 26 to 1.83, 29 to 1.90, 35 to 2.96, 53 to 2.66, 79 to 2.54,
        )[z]
        val ionisation = mapOf(
            1 to 1312.0, 2 to 2372.3, 3 to 520.2, 4 to 899.5, 5 to 800.6, 6 to 1086.5,
            7 to 1402.3, 8 to 1313.9, 9 to 1681.0, 10 to 2080.7, 11 to 495.8, 12 to 737.7,
            13 to 577.5, 14 to 786.5, 15 to 1011.8, 16 to 999.6, 17 to 1251.2, 18 to 1520.6,
            19 to 418.8, 20 to 589.8,
        )[z]
        return PhysicalProperties(electronegativityPauling = electronegativity, firstIonisationEnergyKjMol = ionisation)
    }
}
