package com.indianservers.smartboard.smartboard.multisubject

import com.indianservers.smartboard.smartboard.intelligence.SmartBoardContextElement
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardLessonContext
import com.indianservers.smartboard.smartboard.models.BiologyContentType
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionType
import com.indianservers.smartboard.smartboard.models.EnglishTextType
import com.indianservers.smartboard.smartboard.models.MathExpressionType
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.SmartBoardAction
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardConceptCandidate
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionInput
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectAnalysis
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectClassification
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectHandler
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectMode
import com.indianservers.smartboard.smartboard.models.SubjectCandidate
import com.indianservers.smartboard.smartboard.models.SubjectClassificationSource
import com.indianservers.smartboard.smartboard.models.SubjectConfidenceLevel
import com.indianservers.smartboard.smartboard.models.SubjectEvidence
import com.indianservers.smartboard.smartboard.recognition.MathHandwritingRecognitionProvider
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionClassifier
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionOptions
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import java.security.MessageDigest
import java.util.Locale

data class StrokeGroupMetadata(val strokeCount: Int, val aspectRatio: Float, val hasClosedShapes: Boolean)
data class SmartBoardImageRegion(val bounds: SmartBoardBounds, val diagramHint: String?, val labelCandidates: List<String>)

data class SubjectDetectionRequest(
    val boardMode: SmartBoardSubjectMode,
    val recognizedText: String?,
    val recognizedLatex: String?,
    val strokeMetadata: StrokeGroupMetadata?,
    val imageRegion: SmartBoardImageRegion?,
    val nearbyElementContext: List<SmartBoardContextElement>,
    val lessonContext: SmartBoardLessonContext?,
    val availableSubjects: Set<SmartBoardSubject>,
)

data class SubjectDetectionResult(
    val primarySubject: SmartBoardSubject?,
    val candidates: List<SubjectCandidate>,
    val confidenceLevel: SubjectConfidenceLevel,
    val detectedConcepts: List<SmartBoardConceptCandidate>,
    val requiresConfirmation: Boolean,
    val warnings: List<String>,
    val cacheHit: Boolean = false,
    val detectionLatencyMillis: Long = 0L,
) {
    fun classification(userConfirmed: Boolean = false) = SmartBoardSubjectClassification(
        primarySubject,
        candidates.filterNot { it.subject == primarySubject }.take(4),
        candidates.firstOrNull { it.subject == primarySubject }?.confidence,
        if (userConfirmed) SubjectClassificationSource.USER_SELECTION else SubjectClassificationSource.LOCAL_RULES,
        userConfirmed,
        inheritedFromBoardMode = false,
        warnings,
    )
}

interface SmartBoardSubjectDetector {
    suspend fun detect(request: SubjectDetectionRequest): SubjectDetectionResult
}

/**
 * Bounded deterministic detector. It uses observable symbols, terms, units and diagram metadata;
 * Board text is data only and can never register a handler or invoke an action.
 */
class DeterministicSmartBoardSubjectDetector(
    private val cacheLimit: Int = 128,
) : SmartBoardSubjectDetector {
    private val cache = object : LinkedHashMap<String, SubjectDetectionResult>(cacheLimit, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SubjectDetectionResult>?) = size > cacheLimit
    }

    override suspend fun detect(request: SubjectDetectionRequest): SubjectDetectionResult {
        val startedAt = System.nanoTime()
        val fingerprint = fingerprint(request)
        synchronized(cache) { cache[fingerprint] }?.let { return it.copy(cacheHit = true, detectionLatencyMillis = 0L) }
        val source = listOfNotNull(request.recognizedText, request.recognizedLatex).joinToString("\n").trim().take(8_000)
        val lower = source.lowercase(Locale.ROOT)
        val evidence = linkedMapOf<SmartBoardSubject, MutableList<SubjectEvidence>>()
        val scores = linkedMapOf<SmartBoardSubject, Float>()
        fun add(subject: SmartBoardSubject, score: Float, item: SubjectEvidence) {
            if (subject !in request.availableSubjects) return
            scores[subject] = (scores[subject] ?: 0f) + score
            evidence.getOrPut(subject, ::mutableListOf) += item
        }

        detectPhysics(source, lower) { score, item -> add(SmartBoardSubject.PHYSICS, score, item) }
        detectChemistry(source, lower) { score, item -> add(SmartBoardSubject.CHEMISTRY, score, item) }
        detectBiology(lower, request.imageRegion) { score, item -> add(SmartBoardSubject.BIOLOGY, score, item) }
        detectEnglish(source, lower) { score, item -> add(SmartBoardSubject.ENGLISH, score, item) }
        detectMathematics(source) { score, item -> add(SmartBoardSubject.MATHEMATICS, score, item) }

        request.lessonContext?.conceptId?.let { concept ->
            subjectFromConcept(concept)?.let { add(it, .16f, SubjectEvidence.ConceptMatch(concept)) }
        }
        val selectedMode = request.boardMode.selection
        if (selectedMode !in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL)) {
            add(selectedMode, if (request.boardMode.userSelected) .22f else .12f, SubjectEvidence.UserContext("Board mode prefers ${selectedMode.name.lowercase()}."))
        }
        val ranked = scores.entries
            .map { (subject, score) -> SubjectCandidate(subject, score.coerceIn(0f, .98f), evidence[subject].orEmpty().take(24)) }
            .filter { (it.confidence ?: 0f) >= .18f }
            .sortedByDescending { it.confidence }
            .take(5)
        val first = ranked.firstOrNull()
        val second = ranked.getOrNull(1)
        val separation = (first?.confidence ?: 0f) - (second?.confidence ?: 0f)
        val level = when {
            first == null -> SubjectConfidenceLevel.UNRESOLVED
            first.confidence!! >= .80f && separation >= .18f -> SubjectConfidenceLevel.HIGH
            first.confidence >= .55f && separation >= .08f -> SubjectConfidenceLevel.MEDIUM
            else -> SubjectConfidenceLevel.LOW
        }
        val primary = first?.subject.takeIf { level != SubjectConfidenceLevel.UNRESOLVED }
        val mismatch = request.boardMode.locked && selectedMode !in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL) &&
            primary != null && primary != selectedMode
        val result = SubjectDetectionResult(
            primary,
            ranked,
            level,
            primary?.let { concepts(it, source, lower, first?.confidence ?: .5f) }.orEmpty(),
            requiresConfirmation = level != SubjectConfidenceLevel.HIGH || mismatch,
            warnings = buildList {
                if (source.isBlank() && request.imageRegion == null) add("No recognized content was available for subject detection.")
                if (mismatch) add("Detected content differs from the locked Board subject; the Board mode was not changed.")
                if (level in setOf(SubjectConfidenceLevel.LOW, SubjectConfidenceLevel.UNRESOLVED)) add("Choose a subject before subject-specific processing.")
            },
            detectionLatencyMillis = (System.nanoTime() - startedAt) / 1_000_000L,
        )
        synchronized(cache) { cache[fingerprint] = result }
        return result
    }

    private fun detectPhysics(source: String, lower: String, add: (Float, SubjectEvidence) -> Unit) {
        val units = Regex("""\b(kg|m/s(?:\^?2|²)?|newton|joule|volt|amp(?:ere)?|ohm|watt|hz|pascal)\b""", RegexOption.IGNORE_CASE)
            .findAll(source).map { it.value }.distinct().take(4).toList()
        units.forEach { add(.24f, SubjectEvidence.UnitMatch(it)) }
        listOf("velocity", "acceleration", "force", "current", "voltage", "energy", "momentum", "circuit", "lens", "wave")
            .filter(lower::contains).take(4).forEach { add(.18f, SubjectEvidence.RecognizedTerm(it)) }
        if (Regex("""\bF\s*=\s*ma\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) add(.58f, SubjectEvidence.FormulaMatch("newton-second-law"))
        if (Regex("""\b(v\s*=\s*u\s*\+\s*a\s*t|V\s*=\s*I\s*R)\b""").containsMatchIn(source)) add(.50f, SubjectEvidence.FormulaMatch("physics-formula"))
    }

    private fun detectChemistry(source: String, lower: String, add: (Float, SubjectEvidence) -> Unit) {
        val reaction = Regex("""(?:->|→|⇌|<=>)""").containsMatchIn(source)
        val formula = Regex("""(?:[A-Z][a-z]?[0-9₀₁₂₃₄₅₆₇₈₉]*){2,}""").containsMatchIn(source)
        val state = Regex("""\((?:s|l|g|aq)\)""", RegexOption.IGNORE_CASE).containsMatchIn(source)
        if (reaction && formula) add(.88f, SubjectEvidence.SymbolPattern("Chemical formulae joined by a reaction arrow"))
        else if (formula && source.length >= 3) add(.42f, SubjectEvidence.SymbolPattern("Multiple valid element-symbol groups with subscripts"))
        if (state) add(.24f, SubjectEvidence.SymbolPattern("Chemical state symbol"))
        listOf("mole", "molarity", "oxidation", "reduction", "acid", "base", "stoichiometry")
            .filter(lower::contains).take(4).forEach { add(.20f, SubjectEvidence.RecognizedTerm(it)) }
    }

    private fun detectBiology(lower: String, image: SmartBoardImageRegion?, add: (Float, SubjectEvidence) -> Unit) {
        val terms = listOf(
            "cell", "nucleus", "mitochondria", "chloroplast", "photosynthesis", "respiration",
            "mitosis", "digestion", "genetics", "chromosome", "taxonomy", "ecosystem",
        ).filter(lower::contains).take(5)
        terms.forEach { add(.20f, SubjectEvidence.RecognizedTerm(it)) }
        if (terms.size >= 2) add(.44f, SubjectEvidence.SymbolPattern("Multiple related biological terms"))
        val diagram = image?.diagramHint.orEmpty().lowercase()
        if (diagram.contains("cell") || diagram.contains("organ") || diagram.contains("plant")) {
            add(.58f, SubjectEvidence.DiagramType(image?.diagramHint.orEmpty()))
        }
        if (image?.labelCandidates?.any { it.lowercase() in setOf("nucleus", "cell wall", "membrane", "cytoplasm") } == true) {
            add(.32f, SubjectEvidence.SymbolPattern("Biological diagram labels"))
        }
    }

    private fun detectEnglish(source: String, lower: String, add: (Float, SubjectEvidence) -> Unit) {
        val words = Regex("""[A-Za-z]+(?:'[A-Za-z]+)?""").findAll(source).map { it.value }.toList()
        val sentence = words.size >= 4 && Regex("""[.!?](?:\s|$)""").containsMatchIn(source)
        val proseRatio = if (source.isBlank()) 0f else words.sumOf(String::length).toFloat() / source.length
        if (sentence && proseRatio > .55f) add(.86f, SubjectEvidence.LanguagePattern("English sentence structure and punctuation"))
        else if (words.size >= 8 && proseRatio > .60f) add(.52f, SubjectEvidence.LanguagePattern("English prose"))
        if (lower.contains("fill in the blank") || source.contains("____")) add(.22f, SubjectEvidence.RecognizedTerm("language exercise"))
    }

    private fun detectMathematics(source: String, add: (Float, SubjectEvidence) -> Unit) {
        if (source.isBlank()) return
        if (Regex("""[=+\-*/^√∫ΣΠ]""").containsMatchIn(source)) add(.36f, SubjectEvidence.SymbolPattern("Mathematical operators"))
        if (Regex("""\b(sin|cos|tan|log|lim|int|matrix)\b|\\(frac|sqrt|int)""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            add(.48f, SubjectEvidence.SymbolPattern("Mathematical function or notation"))
        }
        if (Regex("""[A-Za-z]\s*(?:\^?2|²).*=|=.*[A-Za-z]""").containsMatchIn(source)) add(.32f, SubjectEvidence.FormulaMatch("algebraic-equation"))
        if (source.contains('=') && !Regex("""\b(kg|volt|m/s|mol)\b""", RegexOption.IGNORE_CASE).containsMatchIn(source)) {
            add(.20f, SubjectEvidence.SymbolPattern("Pure equation without domain-specific units"))
        }
    }

    private fun concepts(subject: SmartBoardSubject, source: String, lower: String, confidence: Float): List<SmartBoardConceptCandidate> {
        val (id, label, capabilities) = when (subject) {
            SmartBoardSubject.MATHEMATICS -> when {
                Regex("""(?:\^?2|²)""").containsMatchIn(source) && source.contains('=') -> Triple("math.quadratic", "Quadratic equations", listOf("cas", "solver", "graph2d"))
                Regex("""int|∫""", RegexOption.IGNORE_CASE).containsMatchIn(source) -> Triple("math.integration", "Integration", listOf("cas", "graph2d"))
                else -> Triple("math.algebra", "Algebra", listOf("cas", "solver"))
            }
            SmartBoardSubject.PHYSICS -> when {
                lower.contains("force") || Regex("""F\s*=\s*ma""", RegexOption.IGNORE_CASE).containsMatchIn(source) -> Triple("physics.newton2", "Newton's second law", listOf("physics-formulas", "units", "solver"))
                else -> Triple("physics.kinematics", "Kinematics", listOf("physics-formulas", "units", "graph2d"))
            }
            SmartBoardSubject.CHEMISTRY -> Triple("chemistry.equations", "Chemical equations", listOf("chemistry-notation", "periodic-table"))
            SmartBoardSubject.ENGLISH -> Triple("english.writing", if (source.length > 120) "Paragraph writing" else "Grammar and sentences", listOf("handwriting-ocr"))
            SmartBoardSubject.BIOLOGY -> Triple("biology.cell", if (lower.contains("cell")) "Cell biology" else "Biology terminology", listOf("biology-catalogue"))
            else -> return emptyList()
        }
        return listOf(SmartBoardConceptCandidate("$id-candidate", subject, id, label, confidence, listOf("Matched observable content patterns"), null, capabilities))
    }

    private fun subjectFromConcept(value: String) = when {
        value.startsWith("math", true) -> SmartBoardSubject.MATHEMATICS
        value.startsWith("physics", true) -> SmartBoardSubject.PHYSICS
        value.startsWith("chem", true) -> SmartBoardSubject.CHEMISTRY
        value.startsWith("english", true) -> SmartBoardSubject.ENGLISH
        value.startsWith("bio", true) -> SmartBoardSubject.BIOLOGY
        else -> null
    }

    private fun fingerprint(request: SubjectDetectionRequest): String {
        val raw = listOf(
            request.boardMode.selection.name, request.boardMode.locked,
            request.recognizedText.orEmpty().take(8_000), request.recognizedLatex.orEmpty().take(8_000),
            request.imageRegion?.diagramHint.orEmpty(), request.lessonContext?.conceptId.orEmpty(),
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

enum class SmartBoardSubjectCapability {
    HANDWRITING_RECOGNITION, TEXT_OCR, MATHEMATICS_CAS, SOLVER, GRAPHING, STATISTICS, GEOMETRY,
    PHYSICS_FORMULAS, UNIT_CONVERSION, DIMENSIONAL_ANALYSIS, PHYSICS_DIAGRAMS, PHYSICS_VISUALIZATION,
    CHEMISTRY_NOTATION, PERIODIC_TABLE, CHEMISTRY_FORMULAS, CHEMISTRY_LESSONS,
    ENGLISH_TEXT_REVIEW, BIOLOGY_TERMINOLOGY, BIOLOGY_CATALOGUE, BIOLOGY_LESSONS,
}

interface SmartBoardSubjectCapabilityRegistry {
    fun subjects(): Set<SmartBoardSubject>
    fun capabilitiesFor(subject: SmartBoardSubject, conceptId: String? = null): Set<SmartBoardSubjectCapability>
    fun handlerFor(subject: SmartBoardSubject): SmartBoardSubjectHandler?
    fun recognitionProviderFor(subject: SmartBoardSubject): String?
}

class DefaultSmartBoardSubjectCapabilityRegistry(
    handlers: Map<SmartBoardSubject, () -> SmartBoardSubjectHandler>,
) : SmartBoardSubjectCapabilityRegistry {
    private val factories = handlers.toMap()
    private val loaded = mutableMapOf<SmartBoardSubject, SmartBoardSubjectHandler>()
    override fun subjects() = factories.keys
    override fun handlerFor(subject: SmartBoardSubject) = factories[subject]?.let { synchronized(loaded) { loaded.getOrPut(subject, it) } }
    override fun recognitionProviderFor(subject: SmartBoardSubject) =
        if (subject in factories) "existing-mlkit-digital-ink" else null

    override fun capabilitiesFor(subject: SmartBoardSubject, conceptId: String?) = when (subject) {
        SmartBoardSubject.MATHEMATICS -> setOf(
            SmartBoardSubjectCapability.HANDWRITING_RECOGNITION, SmartBoardSubjectCapability.MATHEMATICS_CAS,
            SmartBoardSubjectCapability.SOLVER, SmartBoardSubjectCapability.GRAPHING,
            SmartBoardSubjectCapability.STATISTICS, SmartBoardSubjectCapability.GEOMETRY,
        )
        SmartBoardSubject.PHYSICS -> setOf(
            SmartBoardSubjectCapability.HANDWRITING_RECOGNITION, SmartBoardSubjectCapability.PHYSICS_FORMULAS,
            SmartBoardSubjectCapability.UNIT_CONVERSION, SmartBoardSubjectCapability.DIMENSIONAL_ANALYSIS,
            SmartBoardSubjectCapability.PHYSICS_DIAGRAMS, SmartBoardSubjectCapability.PHYSICS_VISUALIZATION,
            SmartBoardSubjectCapability.SOLVER, SmartBoardSubjectCapability.GRAPHING,
        )
        SmartBoardSubject.CHEMISTRY -> setOf(
            SmartBoardSubjectCapability.HANDWRITING_RECOGNITION, SmartBoardSubjectCapability.CHEMISTRY_NOTATION,
            SmartBoardSubjectCapability.PERIODIC_TABLE, SmartBoardSubjectCapability.CHEMISTRY_FORMULAS,
            SmartBoardSubjectCapability.UNIT_CONVERSION, SmartBoardSubjectCapability.CHEMISTRY_LESSONS,
        )
        SmartBoardSubject.ENGLISH -> setOf(
            SmartBoardSubjectCapability.HANDWRITING_RECOGNITION, SmartBoardSubjectCapability.TEXT_OCR,
            SmartBoardSubjectCapability.ENGLISH_TEXT_REVIEW,
        )
        SmartBoardSubject.BIOLOGY -> setOf(
            SmartBoardSubjectCapability.HANDWRITING_RECOGNITION, SmartBoardSubjectCapability.TEXT_OCR,
            SmartBoardSubjectCapability.BIOLOGY_TERMINOLOGY, SmartBoardSubjectCapability.BIOLOGY_CATALOGUE,
            SmartBoardSubjectCapability.BIOLOGY_LESSONS,
        )
        SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL -> emptySet()
    }
}

class Phase1SubjectRecognitionHandler(
    override val subject: SmartBoardSubject,
    private val provider: MathHandwritingRecognitionProvider,
) : SmartBoardSubjectHandler {
    init { require(subject in setOf(SmartBoardSubject.CHEMISTRY, SmartBoardSubject.ENGLISH, SmartBoardSubject.BIOLOGY, SmartBoardSubject.GENERAL)) }
    override suspend fun analyze(input: SmartBoardRecognitionInput): SmartBoardSubjectAnalysis {
        require(input.subject == subject)
        val result = provider.recognize(
            MathRecognitionInput(input.strokes, input.bounds, input.rasterPng, MathRecognitionRequestBuilder.fingerprint(input)),
            MathRecognitionOptions(preferLatex = subject == SmartBoardSubject.CHEMISTRY),
        )
        return SmartBoardSubjectAnalysis(
            subject,
            "${subject.name.lowercase().replaceFirstChar(Char::titlecase)} recognition is ready for user confirmation; no solving or correction was performed.",
            result,
            interpretationAttributes(subject, result.plainText ?: result.latex),
        )
    }
    override fun supportedActions(analysis: SmartBoardSubjectAnalysis) =
        if (analysis.recognition == null) listOf(SmartBoardAction.RetryRecognition)
        else listOf(SmartBoardAction.InsertExpression, SmartBoardAction.RetryRecognition)
}

data class UnifiedRecognitionRequest(
    val input: SmartBoardRecognitionInput,
    val boardMode: SmartBoardSubjectMode,
    val imageRegion: SmartBoardImageRegion? = null,
)
data class UnifiedRecognitionResult(
    val recognition: MathRecognitionResult,
    val detection: SubjectDetectionResult,
    val analysis: SmartBoardSubjectAnalysis?,
    val routedSubject: SmartBoardSubject?,
    val providerId: String,
)
interface SmartBoardRecognitionOrchestrator {
    suspend fun recognize(request: UnifiedRecognitionRequest): UnifiedRecognitionResult
}

class DefaultSmartBoardRecognitionOrchestrator(
    private val provider: MathHandwritingRecognitionProvider,
    private val detector: SmartBoardSubjectDetector,
    private val registry: SmartBoardSubjectCapabilityRegistry,
) : SmartBoardRecognitionOrchestrator {
    override suspend fun recognize(request: UnifiedRecognitionRequest): UnifiedRecognitionResult {
        val input = request.input
        val recognition = provider.recognize(
            MathRecognitionInput(input.strokes, input.bounds, input.rasterPng, MathRecognitionRequestBuilder.fingerprint(input)),
            MathRecognitionOptions(preferLatex = request.boardMode.selection != SmartBoardSubject.ENGLISH),
        )
        val detection = detector.detect(
            SubjectDetectionRequest(
                request.boardMode,
                recognition.plainText,
                recognition.latex,
                StrokeGroupMetadata(input.strokes.size, input.bounds.width / input.bounds.height.coerceAtLeast(1f), false),
                request.imageRegion,
                emptyList(),
                null,
                registry.subjects(),
            ),
        )
        val manual = request.boardMode.selection.takeIf { it !in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL) }
        val routed = when {
            request.boardMode.locked && manual != null -> manual
            manual != null && detection.confidenceLevel != SubjectConfidenceLevel.HIGH -> manual
            detection.confidenceLevel == SubjectConfidenceLevel.HIGH -> detection.primarySubject
            manual != null -> manual
            else -> null
        }
        val analysis = routed?.let {
            SmartBoardSubjectAnalysis(
                it,
                "Subject-aware recognition routed through the existing on-device handwriting provider.",
                recognition,
                interpretationAttributes(it, recognition.plainText ?: recognition.latex),
            )
        }
        return UnifiedRecognitionResult(recognition, detection, analysis, routed, "existing-mlkit-digital-ink")
    }
}

fun interpretationAttributes(subject: SmartBoardSubject, source: String): Map<String, String> = when (subject) {
    SmartBoardSubject.CHEMISTRY -> mapOf(
        "expressionType" to chemistryType(source).name,
        "normalized" to normalizeChemistry(source),
    )
    SmartBoardSubject.ENGLISH -> mapOf(
        "textType" to englishType(source).name,
        "languageCode" to "en",
    )
    SmartBoardSubject.BIOLOGY -> mapOf(
        "contentType" to biologyType(source).name,
    )
    else -> mapOf("detectedType" to MathRecognitionClassifier.detect(source).name)
}

fun chemistryType(source: String) = when {
    Regex("""(?:->|→|⇌|<=>)""").containsMatchIn(source) -> ChemistryExpressionType.REACTION
    Regex("""[+-](?:\s|$)|[⁺⁻]""").containsMatchIn(source) -> ChemistryExpressionType.ION
    Regex("""(?:[A-Z][a-z]?\d*){2,}""").containsMatchIn(source) -> ChemistryExpressionType.FORMULA
    Regex("""^[A-Z][a-z]?$""").matches(source.trim()) -> ChemistryExpressionType.ELEMENT_SYMBOL
    Regex("""\b(methyl|ethyl|hydroxyl|carboxyl)\b""", RegexOption.IGNORE_CASE).containsMatchIn(source) -> ChemistryExpressionType.ORGANIC_GROUP
    source.any(Char::isLetter) -> ChemistryExpressionType.CHEMICAL_NAME
    else -> ChemistryExpressionType.UNKNOWN
}

fun normalizeChemistry(source: String): String {
    val subscript = mapOf('₀' to '0', '₁' to '1', '₂' to '2', '₃' to '3', '₄' to '4', '₅' to '5', '₆' to '6', '₇' to '7', '₈' to '8', '₉' to '9')
    return source.trim().replace("⟶", "→").replace("->", "→").map { subscript[it] ?: it }.joinToString("")
}

fun englishType(source: String) = when {
    source.contains('\n') && source.length > 120 -> EnglishTextType.PARAGRAPH
    source.contains("____") -> EnglishTextType.FILL_IN_BLANK
    source.lines().size > 1 && source.lines().all { Regex("""^[-*•\d]""").containsMatchIn(it.trimStart()) } -> EnglishTextType.LIST
    Regex("""[.!?](?:\s|$)""").containsMatchIn(source) -> EnglishTextType.SENTENCE
    source.trim().split(Regex("""\s+""")).size == 1 -> EnglishTextType.WORD
    else -> EnglishTextType.UNKNOWN
}

fun biologyType(source: String): BiologyContentType {
    val lower = source.lowercase()
    return when {
        lower.contains("cell") && listOf("nucleus", "membrane", "cytoplasm").any(lower::contains) -> BiologyContentType.CELL_DIAGRAM
        listOf("gene", "allele", "chromosome", "punnett").any(lower::contains) -> BiologyContentType.GENETICS
        listOf("kingdom", "phylum", "genus", "species").any(lower::contains) -> BiologyContentType.TAXONOMY
        listOf("photosynthesis", "respiration", "mitosis", "digestion").any(lower::contains) -> BiologyContentType.PROCESS_FLOW
        source.any(Char::isLetter) -> BiologyContentType.TERM
        else -> BiologyContentType.UNKNOWN
    }
}
