package com.indianservers.smartboard.biology.repository

import com.indianservers.smartboard.biology.data.BundledBiologyCatalogue
import com.indianservers.smartboard.biology.domain.BiologyCatalogueValidator
import com.indianservers.smartboard.biology.domain.BiologyValidationReport
import com.indianservers.smartboard.biology.model.*

interface BiologyRepository {
    fun getDomains(): List<BiologyDomain>
    fun getDomain(id: String): BiologyDomain?
    fun getUnits(domainId: String): List<BiologyUnit>
    fun getUnit(id: String): BiologyUnit?
    fun getChapters(unitId: String): List<BiologyChapter>
    fun getChapter(id: String): BiologyChapter?
    fun getTopics(chapterId: String): List<BiologyTopic>
    fun getTopic(id: String): BiologyTopic?
    fun getConcept(id: String): BiologyConcept?
    fun getConceptForTopic(topicId: String): BiologyConcept?
    fun search(query: String, maximumLevel: BiologyLearningLevel): List<BiologySearchResult>
    fun getGlossaryTerms(): List<GlossaryTerm>
    fun getRelatedConcepts(conceptId: String): List<BiologyConcept>
    fun validate(): BiologyValidationReport
}

class OfflineBiologyRepository(
    private val catalogue: BiologyCatalogue = BundledBiologyCatalogue.catalogue,
) : BiologyRepository {
    private val domains = catalogue.domains.associateBy { it.id }; private val units = catalogue.units.associateBy { it.id }
    private val chapters = catalogue.chapters.associateBy { it.id }; private val topics = catalogue.topics.associateBy { it.id }
    private val concepts = catalogue.concepts.associateBy { it.id }

    init { require(BiologyCatalogueValidator.validate(catalogue).valid) { BiologyCatalogueValidator.validate(catalogue).errors.joinToString("; ") } }

    override fun getDomains() = catalogue.domains
    override fun getDomain(id: String) = domains[id]
    override fun getUnits(domainId: String) = catalogue.units.filter { it.domainId == domainId }
    override fun getUnit(id: String) = units[id]
    override fun getChapters(unitId: String) = catalogue.chapters.filter { it.unitId == unitId }
    override fun getChapter(id: String) = chapters[id]
    override fun getTopics(chapterId: String) = catalogue.topics.filter { it.chapterId == chapterId }
    override fun getTopic(id: String) = topics[id]
    override fun getConcept(id: String) = concepts[id]
    override fun getConceptForTopic(topicId: String) = catalogue.concepts.firstOrNull { it.topicId == topicId }
    override fun getGlossaryTerms() = catalogue.glossary
    override fun getRelatedConcepts(conceptId: String) = concepts[conceptId]?.relatedConceptIds.orEmpty().mapNotNull(concepts::get)
    override fun validate() = BiologyCatalogueValidator.validate(catalogue)

    override fun search(query: String, maximumLevel: BiologyLearningLevel): List<BiologySearchResult> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        val results = mutableListOf<BiologySearchResult>()
        catalogue.domains.filter { matches(needle, it.title, it.description, it.keywords) }.forEach { results += BiologySearchResult(it.id, it.title, BiologyNodeType.Domain, it.id, it.minimumLevel, it.description, false, false) }
        catalogue.units.filter { it.minimumLevel.rank <= maximumLevel.rank && matches(needle, it.title, it.description, it.keywords) }.forEach { results += BiologySearchResult(it.id, it.title, BiologyNodeType.Unit, it.domainId, it.minimumLevel, it.description, false, false) }
        catalogue.chapters.filter { it.minimumLevel.rank <= maximumLevel.rank && matches(needle, it.title, it.description, it.keywords) }.forEach { chapter -> val domainId = units.getValue(chapter.unitId).domainId; results += BiologySearchResult(chapter.id, chapter.title, BiologyNodeType.Chapter, domainId, chapter.minimumLevel, chapter.description, false, false) }
        catalogue.topics.filter { it.minimumLevel.rank <= maximumLevel.rank && matches(needle, it.title, it.description, it.keywords) }.forEach { topic -> val domainId = units.getValue(chapters.getValue(topic.chapterId).unitId).domainId; val concept = getConceptForTopic(topic.id); results += BiologySearchResult(topic.id, topic.title, BiologyNodeType.Topic, domainId, topic.minimumLevel, topic.description, concept?.diagramIds?.isNotEmpty() == true, topic.future3DObjectId != null) }
        catalogue.glossary.filter { it.term.lowercase().contains(needle) || it.synonyms.any { synonym -> synonym.lowercase().contains(needle) } }.forEach { results += BiologySearchResult(it.id, it.term, BiologyNodeType.Glossary, it.domainId, BiologyLearningLevel.FOUNDATION, it.schoolDefinition, false, false) }
        return results.distinctBy { it.type to it.id }.take(100)
    }

    private fun matches(query: String, title: String, description: String, keywords: Set<String>) = title.lowercase().contains(query) || description.lowercase().contains(query) || keywords.any { it.contains(query) }
}
