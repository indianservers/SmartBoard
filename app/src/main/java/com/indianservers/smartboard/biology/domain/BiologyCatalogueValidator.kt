package com.indianservers.smartboard.biology.domain

import com.indianservers.smartboard.biology.model.*

data class BiologyValidationReport(val errors: List<String>, val warnings: List<String>) { val valid: Boolean get() = errors.isEmpty() }

object BiologyCatalogueValidator {
    fun validate(catalogue: BiologyCatalogue): BiologyValidationReport {
        val errors = mutableListOf<String>(); val warnings = mutableListOf<String>()
        fun <T> unique(label: String, items: List<T>, id: (T) -> String) { if (items.map(id).toSet().size != items.size) errors += "Duplicate $label ID." }
        unique("domain", catalogue.domains) { it.id }; unique("unit", catalogue.units) { it.id }; unique("chapter", catalogue.chapters) { it.id }
        unique("topic", catalogue.topics) { it.id }; unique("concept", catalogue.concepts) { it.id }; unique("diagram", catalogue.diagrams) { it.id }
        unique("glossary", catalogue.glossary) { it.id }; unique("quiz", catalogue.quizzes) { it.id }; unique("future 3D", catalogue.future3D) { it.objectId }
        val domainIds = catalogue.domains.map { it.id }.toSet(); val unitIds = catalogue.units.map { it.id }.toSet(); val chapterIds = catalogue.chapters.map { it.id }.toSet()
        val topicIds = catalogue.topics.map { it.id }.toSet(); val conceptIds = catalogue.concepts.map { it.id }.toSet(); val diagramIds = catalogue.diagrams.map { it.id }.toSet(); val quizIds = catalogue.quizzes.map { it.id }.toSet()
        catalogue.domains.forEach { domain -> if (domain.unitIds.any { it !in unitIds }) errors += "${domain.id} has broken unit link." }
        catalogue.units.forEach { unit -> if (unit.domainId !in domainIds || unit.chapterIds.any { it !in chapterIds }) errors += "${unit.id} has broken hierarchy link." }
        catalogue.chapters.forEach { chapter -> if (chapter.unitId !in unitIds || chapter.topicIds.any { it !in topicIds }) errors += "${chapter.id} has broken hierarchy link." }
        catalogue.topics.forEach { topic -> if (topic.chapterId !in chapterIds || topic.conceptIds.any { it !in conceptIds }) errors += "${topic.id} has broken hierarchy link." }
        catalogue.concepts.forEach { concept ->
            if (concept.topicId !in topicIds || concept.relatedConceptIds.any { it !in conceptIds } || concept.diagramIds.any { it !in diagramIds } || concept.quizQuestionIds.any { it !in quizIds }) errors += "${concept.id} has broken content link."
            if (concept.summary.isBlank() || concept.learningObjectives.isEmpty()) errors += "${concept.id} has no safe overview."
            if (concept.status == BiologyContentStatus.OverviewReady && concept.plannedSections.isEmpty()) errors += "${concept.id} incomplete overview has no planned sections."
            concept.blocks.filterIsInstance<BiologyContentBlock.Diagram>().forEach { if (it.diagramId !in diagramIds) errors += "${concept.id} block has broken diagram." }
        }
        catalogue.glossary.forEach { term -> if (term.domainId !in domainIds || term.conceptId != null && term.conceptId !in conceptIds) errors += "${term.id} has broken glossary link." }
        catalogue.quizzes.forEach { question ->
            if (question.conceptId !in conceptIds || question.correctAnswers.isEmpty() || question.correctAnswers.any { it !in question.options.indices }) errors += "${question.id} has invalid answer configuration."
        }
        catalogue.future3D.forEach { metadata ->
            if (metadata.conceptId !in conceptIds || metadata.fallbackDiagramId !in diagramIds) errors += "${metadata.objectId} has invalid future 3D fallback."
        }
        if (catalogue.schemaVersion < 1) errors += "Invalid Biology schema version."
        if (catalogue.domains.size != 22) warnings += "Expected the requested 22-domain catalogue."
        if (hasPrerequisiteCycle(catalogue.concepts.associate { it.id to it.prerequisites })) errors += "Circular concept prerequisite chain."
        return BiologyValidationReport(errors, warnings)
    }

    private fun hasPrerequisiteCycle(graph: Map<String, List<String>>): Boolean {
        val visiting = mutableSetOf<String>(); val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (id in visiting) return true; if (id in visited) return false
            visiting += id
            if (graph[id].orEmpty().filter { it in graph }.any(::visit)) return true
            visiting -= id; visited += id; return false
        }
        return graph.keys.any(::visit)
    }
}
