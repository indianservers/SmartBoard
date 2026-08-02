package com.indianservers.smartboard.chemistry.model

enum class ElementCategory(val label: String, val isMetal: Boolean) {
    AlkaliMetal("Alkali metal", true),
    AlkalineEarthMetal("Alkaline-earth metal", true),
    TransitionMetal("Transition metal", true),
    PostTransitionMetal("Post-transition metal", true),
    Metalloid("Metalloid", false),
    ReactiveNonmetal("Reactive nonmetal", false),
    NobleGas("Noble gas", false),
    Lanthanide("Lanthanide", true),
    Actinide("Actinide", true),
    Unknown("Unknown or predicted properties", false),
}

enum class MatterState { Solid, Liquid, Gas, Unknown }
enum class PeriodicBlock { S, P, D, F }
enum class OccurrenceKind { Natural, NaturalTrace, Synthetic }

data class OrbitalOccupancy(
    val principalLevel: Int,
    val subshell: Char,
    val electrons: Int,
) {
    val capacity: Int get() = when (subshell) { 's' -> 2; 'p' -> 6; 'd' -> 10; 'f' -> 14; else -> 0 }
    val label: String get() = "$principalLevel$subshell"
}

data class ElectronConfiguration(
    val full: String,
    val nobleGasShorthand: String,
    val shellDistribution: List<Int>,
    val orbitals: List<OrbitalOccupancy>,
    val electronCount: Int,
) {
    val outerShellElectrons: Int get() = shellDistribution.lastOrNull() ?: 0
    val unpairedElectronEstimate: Int get() = orbitals.sumOf { orbital ->
        val orbitalCount = when (orbital.subshell) { 's' -> 1; 'p' -> 3; 'd' -> 5; 'f' -> 7; else -> 0 }
        minOf(orbital.electrons, orbitalCount * 2 - orbital.electrons).coerceAtLeast(0)
    }
}

data class Isotope(
    val massNumber: Int,
    val abundancePercent: Double?,
    val stable: Boolean,
    val note: String,
)

data class PhysicalProperties(
    val electronegativityPauling: Double? = null,
    val firstIonisationEnergyKjMol: Double? = null,
    val atomicRadiusPm: Double? = null,
    val densityGcm3: Double? = null,
    val meltingPointK: Double? = null,
    val boilingPointK: Double? = null,
)

data class DiscoveryInformation(
    val yearLabel: String? = null,
    val attribution: String? = null,
    val nameOrigin: String? = null,
)

data class ChemicalElement(
    val atomicNumber: Int,
    val symbol: String,
    val name: String,
    val standardAtomicWeight: Double,
    val atomicWeightDisplay: String,
    val group: Int?,
    val period: Int,
    val block: PeriodicBlock,
    val category: ElementCategory,
    val standardState: MatterState,
    val electronConfiguration: ElectronConfiguration,
    val representativeIsotope: Isotope,
    val valenceElectrons: Int,
    val commonOxidationStates: List<Int> = emptyList(),
    val physicalProperties: PhysicalProperties = PhysicalProperties(),
    val discovery: DiscoveryInformation = DiscoveryInformation(),
    val commonUses: List<String> = emptyList(),
    val naturalOccurrence: String = "Data enrichment scheduled for a later Chemistry phase.",
    val safetyNotes: String = "Consult element-specific safety guidance before laboratory handling.",
    val radioactive: Boolean,
    val occurrenceKind: OccurrenceKind,
    val stableIsotopeSummary: String,
    val description: String,
) {
    val protonCount: Int get() = atomicNumber
    val neutralElectronCount: Int get() = atomicNumber
    val representativeNeutronCount: Int get() = representativeIsotope.massNumber - atomicNumber
    fun electronCountForCharge(charge: Int): Int = (atomicNumber - charge).coerceAtLeast(0)
}

enum class PeriodicTrendProperty {
    AtomicRadius, Electronegativity, FirstIonisationEnergy, Density, MeltingPoint, BoilingPoint,
}
