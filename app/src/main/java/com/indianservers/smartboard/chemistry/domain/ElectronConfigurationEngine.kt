package com.indianservers.smartboard.chemistry.domain

import com.indianservers.smartboard.chemistry.model.ElectronConfiguration
import com.indianservers.smartboard.chemistry.model.OrbitalOccupancy

object ElectronConfigurationEngine {
    private data class Orbital(val n: Int, val subshell: Char, val capacity: Int)

    private val fillingOrder = listOf(
        Orbital(1, 's', 2), Orbital(2, 's', 2), Orbital(2, 'p', 6), Orbital(3, 's', 2),
        Orbital(3, 'p', 6), Orbital(4, 's', 2), Orbital(3, 'd', 10), Orbital(4, 'p', 6),
        Orbital(5, 's', 2), Orbital(4, 'd', 10), Orbital(5, 'p', 6), Orbital(6, 's', 2),
        Orbital(4, 'f', 14), Orbital(5, 'd', 10), Orbital(6, 'p', 6), Orbital(7, 's', 2),
        Orbital(5, 'f', 14), Orbital(6, 'd', 10), Orbital(7, 'p', 6),
    )

    private val groundStateOverrides = mapOf(
        24 to mapOf("4s" to 1, "3d" to 5), 29 to mapOf("4s" to 1, "3d" to 10),
        41 to mapOf("5s" to 1, "4d" to 4), 42 to mapOf("5s" to 1, "4d" to 5),
        44 to mapOf("5s" to 1, "4d" to 7), 45 to mapOf("5s" to 1, "4d" to 8),
        46 to mapOf("5s" to 0, "4d" to 10), 47 to mapOf("5s" to 1, "4d" to 10),
        57 to mapOf("4f" to 0, "5d" to 1), 58 to mapOf("4f" to 1, "5d" to 1),
        64 to mapOf("4f" to 7, "5d" to 1), 78 to mapOf("6s" to 1, "5d" to 9),
        79 to mapOf("6s" to 1, "5d" to 10), 89 to mapOf("5f" to 0, "6d" to 1),
        90 to mapOf("5f" to 0, "6d" to 2), 91 to mapOf("5f" to 2, "6d" to 1),
        92 to mapOf("5f" to 3, "6d" to 1), 93 to mapOf("5f" to 4, "6d" to 1),
        96 to mapOf("5f" to 7, "6d" to 1), 103 to mapOf("6d" to 0, "7p" to 1),
    )

    private val nobleGasCores = listOf(
        2 to "He", 10 to "Ne", 18 to "Ar", 36 to "Kr", 54 to "Xe", 86 to "Rn",
    )

    fun neutral(atomicNumber: Int): ElectronConfiguration = forElectronCount(atomicNumber, applyGroundStateExceptions = true)

    fun ion(atomicNumber: Int, charge: Int): ElectronConfiguration {
        require(atomicNumber in 1..118) { "atomic number must be in 1..118" }
        return forElectronCount((atomicNumber - charge).coerceAtLeast(0), applyGroundStateExceptions = charge == 0)
    }

    fun forElectronCount(electronCount: Int, applyGroundStateExceptions: Boolean = false): ElectronConfiguration {
        require(electronCount in 0..118) { "electron count must be in 0..118" }
        var remaining = electronCount
        val occupancies = fillingOrder.associate { orbital ->
            val filled = minOf(remaining, orbital.capacity)
            remaining -= filled
            "${orbital.n}${orbital.subshell}" to filled
        }.toMutableMap()
        if (applyGroundStateExceptions) groundStateOverrides[electronCount]?.forEach { (label, count) -> occupancies[label] = count }
        val orbitals = fillingOrder.mapNotNull { orbital ->
            val count = occupancies.getValue("${orbital.n}${orbital.subshell}")
            if (count == 0) null else OrbitalOccupancy(orbital.n, orbital.subshell, count)
        }
        check(orbitals.sumOf { it.electrons } == electronCount) { "electron configuration total mismatch for $electronCount" }
        val canonical = orbitals.sortedWith(compareBy<OrbitalOccupancy> { it.principalLevel }.thenBy { "spdf".indexOf(it.subshell) })
        val full = canonical.joinToString(" ") { "${it.label}${superscript(it.electrons)}" }
        val core = nobleGasCores.lastOrNull { it.first < electronCount }
        val shorthandOrbitals = if (core == null) canonical else removeCore(canonical, core.first)
        val shorthand = if (core == null) full else "[${core.second}] " + shorthandOrbitals.joinToString(" ") { "${it.label}${superscript(it.electrons)}" }
        val highestShell = canonical.maxOfOrNull { it.principalLevel } ?: 0
        val shells = (1..highestShell).map { shell -> canonical.filter { it.principalLevel == shell }.sumOf { it.electrons } }
        return ElectronConfiguration(full, shorthand.trim(), shells, canonical, electronCount)
    }

    private fun removeCore(orbitals: List<OrbitalOccupancy>, coreElectrons: Int): List<OrbitalOccupancy> {
        var remainingCore = coreElectrons
        val coreByLabel = fillingOrder.associate { orbital ->
            val filled = minOf(remainingCore, orbital.capacity)
            remainingCore -= filled
            "${orbital.n}${orbital.subshell}" to filled
        }
        return orbitals.mapNotNull { orbital ->
            val left = orbital.electrons - coreByLabel.getValue(orbital.label)
            if (left == 0) null else orbital.copy(electrons = left)
        }
    }

    private fun superscript(value: Int): String = value.toString().map { digit -> "⁰¹²³⁴⁵⁶⁷⁸⁹"[digit.digitToInt()] }.joinToString("")
}
