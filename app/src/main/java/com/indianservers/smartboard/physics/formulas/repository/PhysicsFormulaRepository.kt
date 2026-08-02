package com.indianservers.smartboard.physics.formulas.repository

import com.indianservers.smartboard.physics.formulas.data.BundledPhysicsFormulaData
import com.indianservers.smartboard.physics.formulas.domain.PhysicsFormulaValidator
import com.indianservers.smartboard.physics.formulas.model.*

interface PhysicsFormulaRepository {
    fun getCategories(): List<PhysicsFormulaCategory>
    fun getCategory(id: String): PhysicsFormulaCategory?
    fun getSubcategories(categoryId: String): List<PhysicsFormulaSubcategory>
    fun getSubcategory(id: String): PhysicsFormulaSubcategory?
    fun getFormulas(subcategoryId: String): List<PhysicsFormula>
    fun getFormula(id: String): PhysicsFormula?
    fun search(query: String, filters: PhysicsFormulaFilters = PhysicsFormulaFilters(), bookmarked: Set<String> = emptySet(), recent: List<String> = emptyList()): List<PhysicsFormula>
    fun getRelatedFormulas(id: String): List<PhysicsFormula>
    fun validate(): PhysicsFormulaValidationReport
}

class OfflinePhysicsFormulaRepository(
    private val catalogue: PhysicsFormulaCatalogue = BundledPhysicsFormulaData.catalogue
) : PhysicsFormulaRepository {
    private val categories = catalogue.categories.associateBy { it.id }
    private val subcategories = catalogue.subcategories.associateBy { it.id }
    private val formulas = catalogue.formulas.associateBy { it.id }

    init { require(PhysicsFormulaValidator.validate(catalogue).valid) }

    override fun getCategories() = catalogue.categories
    override fun getCategory(id: String) = categories[id]
    override fun getSubcategories(categoryId: String) = catalogue.subcategories.filter { it.categoryId == categoryId }
    override fun getSubcategory(id: String) = subcategories[id]
    override fun getFormulas(subcategoryId: String) = catalogue.formulas.filter { it.subcategoryId == subcategoryId }
    override fun getFormula(id: String) = formulas[id]

    override fun search(query: String, filters: PhysicsFormulaFilters, bookmarked: Set<String>, recent: List<String>): List<PhysicsFormula> {
        val needle = query.trim().lowercase().normalizeFormulaSearch()
        return catalogue.formulas.filter { formula ->
            val category = categories[formula.categoryId]?.title.orEmpty()
            val subcategory = subcategories[formula.subcategoryId]?.title.orEmpty()
            val haystack = listOf(
                formula.title,
                formula.searchableEquation,
                formula.spokenEquation,
                formula.description,
                category,
                subcategory,
                formula.variables.joinToString { "${it.symbol} ${it.spokenName}" },
                formula.keywords.joinToString()
            ).joinToString().lowercase().normalizeFormulaSearch()
            (needle.isEmpty() || needle in haystack) &&
                formula.minimumLevel.rank <= filters.level.rank &&
                (filters.categoryId == null || formula.categoryId == filters.categoryId) &&
                (filters.subcategoryId == null || formula.subcategoryId == filters.subcategoryId) &&
                (!filters.calculatorOnly || formula.calculator != null) &&
                (!filters.derivationOnly || formula.derivationSteps.isNotEmpty()) &&
                (!filters.bookmarkedOnly || formula.id in bookmarked) &&
                (!filters.recentOnly || formula.id in recent)
        }
    }

    override fun getRelatedFormulas(id: String) = formulas[id]?.relatedFormulaIds.orEmpty().mapNotNull(formulas::get)
    override fun validate() = PhysicsFormulaValidator.validate(catalogue)
}

private fun String.normalizeFormulaSearch() = this
    .replace("\\frac", "")
    .replace("\\sqrt", "sqrt")
    .replace("\\delta", "delta")
    .replace("\\theta", "theta")
    .replace("\\lambda", "lambda")
    .replace("\\pi", "pi")
    .replace("\\phi", "phi")
    .replace("\\left", "")
    .replace("\\right", "")
    .replace(" ", "")
