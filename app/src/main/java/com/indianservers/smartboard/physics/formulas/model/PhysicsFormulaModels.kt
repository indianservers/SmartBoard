package com.indianservers.smartboard.physics.formulas.model

enum class PhysicsFormulaLevel(val label: String, val rank: Int) {
    Foundation("Foundation", 0), Class7("Class 7", 1), Class8("Class 8", 2), Class9("Class 9", 3),
    Class10("Class 10", 4), Class11("Class 11", 5), Class12("Class 12", 6),
    Undergraduate("Undergraduate", 7), Postgraduate("Postgraduate", 8),
}

data class PhysicsFormulaCategory(val id: String, val title: String, val description: String, val subcategoryIds: List<String>)
data class PhysicsFormulaSubcategory(val id: String, val categoryId: String, val title: String, val description: String)
data class PhysicsFormulaVariable(val id: String, val symbol: String, val spokenName: String, val meaning: String, val siUnit: String?, val dimension: String?, val positiveOnly: Boolean = false)
data class PhysicsFormulaForm(val label: String, val equation: String)
data class PhysicsDerivationStep(val equation: String, val explanation: String)
data class PhysicsWorkedExample(val question: String, val substitution: String, val answer: String, val unitCheck: String)
data class PhysicsCalculatorDefinition(val targetVariableIds: Set<String>, val requiredVariableIds: Set<String>, val internalUnitSystem: String = "SI")

data class PhysicsFormula(
    val id: String,
    val categoryId: String,
    val subcategoryId: String,
    val title: String,
    val equation: String,
    val searchableEquation: String,
    val spokenEquation: String,
    val description: String,
    val minimumLevel: PhysicsFormulaLevel,
    val variables: List<PhysicsFormulaVariable>,
    val assumptions: List<String>,
    val limitations: List<String>,
    val alternativeForms: List<PhysicsFormulaForm>,
    val derivationSteps: List<PhysicsDerivationStep>,
    val workedExamples: List<PhysicsWorkedExample>,
    val unitCheck: String,
    val calculator: PhysicsCalculatorDefinition?,
    val relatedFormulaIds: List<String>,
    val relatedConceptIds: List<String>,
    val keywords: Set<String>,
    val featured: Boolean = false,
)

data class PhysicsFormulaCatalogue(
    val schemaVersion: Int,
    val categories: List<PhysicsFormulaCategory>,
    val subcategories: List<PhysicsFormulaSubcategory>,
    val formulas: List<PhysicsFormula>,
)

data class PhysicsFormulaFilters(
    val level: PhysicsFormulaLevel = PhysicsFormulaLevel.Class10,
    val categoryId: String? = null,
    val subcategoryId: String? = null,
    val calculatorOnly: Boolean = false,
    val derivationOnly: Boolean = false,
    val bookmarkedOnly: Boolean = false,
    val recentOnly: Boolean = false,
)

data class PhysicsFormulaValidationReport(val errors: List<String>, val warnings: List<String>) { val valid get() = errors.isEmpty() }

