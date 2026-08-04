package com.indianservers.smartboard.smartboard.presentation

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.indianservers.smartboard.smartboard.canvas.SmartBoardSelection
import com.indianservers.smartboard.smartboard.canvas.SmartBoardStrokeGeometry
import com.indianservers.smartboard.smartboard.domain.AddElementCommand
import com.indianservers.smartboard.smartboard.domain.AddElementsCommand
import com.indianservers.smartboard.smartboard.domain.AddRelationshipsCommand
import com.indianservers.smartboard.smartboard.domain.BackgroundCommand
import com.indianservers.smartboard.smartboard.domain.AssignSubjectClassificationCommand
import com.indianservers.smartboard.smartboard.domain.ChangeBoardSubjectModeCommand
import com.indianservers.smartboard.smartboard.domain.ClearBoardCommand
import com.indianservers.smartboard.smartboard.domain.DeleteElementsCommand
import com.indianservers.smartboard.smartboard.domain.EditMathExpressionCommand
import com.indianservers.smartboard.smartboard.domain.GroupCommand
import com.indianservers.smartboard.smartboard.domain.InsertRecognizedExpressionCommand
import com.indianservers.smartboard.smartboard.domain.MoveElementsCommand
import com.indianservers.smartboard.smartboard.domain.ReorderElementsCommand
import com.indianservers.smartboard.smartboard.domain.ReplaceElementCommand
import com.indianservers.smartboard.smartboard.domain.SmartBoardCommand
import com.indianservers.smartboard.smartboard.domain.SmartBoardCommandHistory
import com.indianservers.smartboard.smartboard.domain.SetStrokeVisibilityCommand
import com.indianservers.smartboard.smartboard.domain.UngroupCommand
import com.indianservers.smartboard.smartboard.domain.duplicateElements
import com.indianservers.smartboard.smartboard.domain.groupRelationship
import com.indianservers.smartboard.smartboard.models.MathExpressionElement
import com.indianservers.smartboard.smartboard.models.BiologyContentElement
import com.indianservers.smartboard.smartboard.models.BiologyContentType
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionElement
import com.indianservers.smartboard.smartboard.models.ChemistryExpressionType
import com.indianservers.smartboard.smartboard.models.EnglishTextElement
import com.indianservers.smartboard.smartboard.models.EnglishTextType
import com.indianservers.smartboard.smartboard.models.PhysicsActionType
import com.indianservers.smartboard.smartboard.models.PhysicsContentType
import com.indianservers.smartboard.smartboard.models.PhysicsDiagramElement
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsTopic
import com.indianservers.smartboard.smartboard.models.ActionResultElement
import com.indianservers.smartboard.smartboard.models.GraphConfigurationElement
import com.indianservers.smartboard.smartboard.models.ImageElement
import com.indianservers.smartboard.smartboard.models.SmartBoardResultKind
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionMode
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionTarget
import com.indianservers.smartboard.smartboard.models.MathRecognitionResult
import com.indianservers.smartboard.smartboard.models.MathRecognitionAlternative
import com.indianservers.smartboard.smartboard.models.SmartBoardBackground
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardClassroomSubjects
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardElement
import com.indianservers.smartboard.smartboard.models.SmartBoardInputMode
import com.indianservers.smartboard.smartboard.models.SmartBoardPoint
import com.indianservers.smartboard.smartboard.models.SmartBoardPreferences
import com.indianservers.smartboard.smartboard.models.SmartBoardRecognitionInput
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationship
import com.indianservers.smartboard.smartboard.models.SmartBoardRelationshipType
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectClassification
import com.indianservers.smartboard.smartboard.models.SubjectClassificationSource
import com.indianservers.smartboard.smartboard.models.SmartBoardSubjectAnalysis
import com.indianservers.smartboard.smartboard.models.SmartBoardTool
import com.indianservers.smartboard.smartboard.models.afterSelecting
import com.indianservers.smartboard.smartboard.models.usesDirectPointerInput
import com.indianservers.smartboard.smartboard.models.SmartBoardViewport
import com.indianservers.smartboard.smartboard.models.StrokeElement
import com.indianservers.smartboard.smartboard.models.ShapeElement
import com.indianservers.smartboard.smartboard.models.TableElement
import com.indianservers.smartboard.smartboard.models.TextElement
import com.indianservers.smartboard.smartboard.models.SmartBoardShapeType
import com.indianservers.smartboard.smartboard.shapes.AutoShapeSuggestion
import com.indianservers.smartboard.smartboard.shapes.DeterministicAutoShapeRecognizer
import com.indianservers.smartboard.smartboard.shapes.SmartBoardStrokeGrouper
import com.indianservers.smartboard.smartboard.persistence.SmartBoardRepository
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.recognition.MlKitMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.MlKitImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.DedicatedOfflineImageMathRecognitionAdapter
import com.indianservers.smartboard.smartboard.recognition.OfflineMathModelState
import com.indianservers.smartboard.smartboard.recognition.OfflineMathModelStatus
import com.indianservers.smartboard.smartboard.recognition.OfflineMathOcrModelPack
import com.indianservers.smartboard.smartboard.recognition.MultimodalMathRecognitionEngine
import com.indianservers.smartboard.smartboard.recognition.StreamingMathRecognitionEngine
import com.indianservers.smartboard.smartboard.recognition.StreamingRecognitionSnapshot
import com.indianservers.smartboard.smartboard.recognition.CorrectionGestureSuggestion
import com.indianservers.smartboard.smartboard.recognition.SmartBoardCorrectionGestureDetector
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionInputRenderer
import com.indianservers.smartboard.smartboard.recognition.MathRecognitionRequestBuilder
import com.indianservers.smartboard.smartboard.recognition.MathematicsSubjectHandler
import com.indianservers.smartboard.smartboard.recognition.SafeLatexPreview
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSubjectRegistry
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSemanticExpressionBuilder
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSpecialistInterpretation
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSpecialistRecognitionRegistry
import com.indianservers.smartboard.smartboard.recognition.SmartBoardWholeBoardUnderstanding
import com.indianservers.smartboard.smartboard.recognition.SmartBoardWholeBoardUnderstandingEngine
import com.indianservers.smartboard.smartboard.recognition.BoundedRecognitionDiagnostics
import com.indianservers.smartboard.smartboard.recognition.ContextualRerankOutcome
import com.indianservers.smartboard.smartboard.recognition.RecognitionConfidenceBucket
import com.indianservers.smartboard.smartboard.recognition.RecognitionContext
import com.indianservers.smartboard.smartboard.recognition.RecognitionDiagnosticEvent
import com.indianservers.smartboard.smartboard.recognition.RecognitionDiagnosticInput
import com.indianservers.smartboard.smartboard.recognition.RecognitionLatencyBucket
import com.indianservers.smartboard.smartboard.recognition.RecognitionPersonalizationProfile
import com.indianservers.smartboard.smartboard.recognition.RecognitionPersonalizationProfileCodec
import com.indianservers.smartboard.smartboard.recognition.RecognitionRerankEvidence
import com.indianservers.smartboard.smartboard.recognition.SmartBoardContextualRecognitionReranker
import com.indianservers.smartboard.smartboard.recognition.SmartBoardRecognitionPersonalizer
import com.indianservers.smartboard.smartboard.models.SemanticExpressionTree
import com.indianservers.smartboard.smartboard.integration.SmartBoardCasAdapter
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalysis
import com.indianservers.smartboard.smartboard.integration.SmartBoardExpressionAnalyzer
import com.indianservers.smartboard.smartboard.integration.SmartBoardGraphAdapter
import com.indianservers.smartboard.smartboard.integration.SmartBoardLatexAdapter
import com.indianservers.smartboard.smartboard.integration.SmartBoardMathAction
import com.indianservers.smartboard.smartboard.integration.SmartBoardStatisticsAdapter
import com.indianservers.smartboard.smartboard.security.SmartBoardSecurityPolicy
import com.indianservers.smartboard.smartboard.media.SmartBoardImageAssetStore
import com.indianservers.smartboard.smartboard.export.SmartBoardExportFormat
import com.indianservers.smartboard.smartboard.export.SmartBoardExporter
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEngine
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorMode
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorRequest
import com.indianservers.smartboard.smartboard.tutor.DefaultUnifiedSmartBoardTutor
import com.indianservers.smartboard.smartboard.tutor.SmartBoardSuggestedPrompt
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorContext
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorContextBuilder
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorConversation
import com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorMessage
import com.indianservers.smartboard.smartboard.tutor.UnifiedTutorMode
import com.indianservers.smartboard.smartboard.tutor.UnifiedTutorRequest
import com.indianservers.smartboard.smartboard.tutor.UnifiedTutorResponse
import com.indianservers.smartboard.smartboard.domain.InsertTutorOutputCommand
import com.indianservers.smartboard.smartboard.physics.PhysicsBoardAnalyzer
import com.indianservers.smartboard.smartboard.physics.PhysicsSmartBoardIntelligenceHandler
import com.indianservers.smartboard.smartboard.physics.PhysicsSmartBoardSubjectHandler
import com.indianservers.smartboard.smartboard.physics.PhysicsPhotoRecognitionAdapter
import com.indianservers.smartboard.smartboard.intelligence.DefaultSmartBoardContextBuilder
import com.indianservers.smartboard.smartboard.intelligence.DefaultSmartBoardIntelligenceOrchestrator
import com.indianservers.smartboard.smartboard.intelligence.DefaultSmartBoardToolRegistry
import com.indianservers.smartboard.smartboard.intelligence.BoundedLocalSmartBoardIntelligenceAnalytics
import com.indianservers.smartboard.smartboard.intelligence.MathematicsSmartBoardIntelligenceHandler
import com.indianservers.smartboard.smartboard.intelligence.PhysicsSubjectIntelligenceAdapter
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardActionHistoryEntry
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardIntelligenceContext
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardIntelligenceEvent
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardIntelligenceEventType
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardRecommendation
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardSessionMemory
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardSessionMemoryManager
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardSubjectIntelligenceRegistry
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardToolCall
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardToolResult
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardUnderstandingRequest
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardUnderstandingResult
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardWorkflowPlan
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardWorkflowRequest
import com.indianservers.smartboard.smartboard.intelligence.SmartBoardWorkflowStepRequest
import com.indianservers.smartboard.smartboard.intelligence.WorkflowStepStatus
import com.indianservers.smartboard.smartboard.multisubject.DefaultSmartBoardRecognitionOrchestrator
import com.indianservers.smartboard.smartboard.multisubject.BoundedLocalSmartBoardMultiSubjectAnalytics
import com.indianservers.smartboard.smartboard.multisubject.DefaultSmartBoardSubjectCapabilityRegistry
import com.indianservers.smartboard.smartboard.multisubject.DeterministicSmartBoardSubjectDetector
import com.indianservers.smartboard.smartboard.multisubject.Phase1SubjectRecognitionHandler
import com.indianservers.smartboard.smartboard.multisubject.SubjectDetectionResult
import com.indianservers.smartboard.smartboard.multisubject.UnifiedRecognitionRequest
import com.indianservers.smartboard.smartboard.multisubject.SmartBoardMultiSubjectEvent
import com.indianservers.smartboard.smartboard.multisubject.SmartBoardMultiSubjectEventType
import com.indianservers.smartboard.smartboard.multisubject.biologyType
import com.indianservers.smartboard.smartboard.multisubject.chemistryType
import com.indianservers.smartboard.smartboard.multisubject.englishType
import com.indianservers.smartboard.smartboard.multisubject.normalizeChemistry
import com.indianservers.smartboard.smartboard.tools.SemanticToolOperation
import com.indianservers.smartboard.smartboard.tools.SemanticToolTarget
import com.indianservers.smartboard.smartboard.tools.SmartBoardClassroomToolFactory
import com.indianservers.smartboard.smartboard.tools.SmartBoardEditableReconstructionEngine
import com.indianservers.smartboard.smartboard.tools.SmartBoardSemanticToolEngine
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SmartBoardRecognitionReview(
    val input: SmartBoardRecognitionInput,
    val result: MathRecognitionResult,
    val editableLatex: String,
    val hideSourceHandwriting: Boolean = false,
    val validationMessage: String? = null,
    val subjectAnalysis: SmartBoardSubjectAnalysis? = null,
    val subjectDetection: SubjectDetectionResult? = null,
    val selectedSubject: SmartBoardSubject? = null,
    val semanticTree: SemanticExpressionTree? = null,
    val specialistInterpretations: List<SmartBoardSpecialistInterpretation> = emptyList(),
    val contextEvidence: List<RecognitionRerankEvidence> = emptyList(),
)

data class SmartBoardStreamingRecognitionSuggestion(
    val input: SmartBoardRecognitionInput,
    val snapshot: StreamingRecognitionSnapshot,
    val contextEvidenceByCandidate: Map<String, List<RecognitionRerankEvidence>> = emptyMap(),
)

data class SmartBoardGraphLaunch(val route: String, val expression: String)

class SmartBoardViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val repository = SmartBoardRepository(application)
    private val history = SmartBoardCommandHistory()
    private val handwritingProvider by lazy { MlKitMathRecognitionAdapter() }
    private val offlineMathModelPack = OfflineMathOcrModelPack(application)
    private val imageRecognitionProvider by lazy {
        DedicatedOfflineImageMathRecognitionAdapter(
            application,
            offlineMathModelPack,
            MlKitImageMathRecognitionAdapter(),
        )
    }
    private val multimodalRecognition by lazy { MultimodalMathRecognitionEngine(handwritingProvider, imageRecognitionProvider) }
    private val streamingRecognitionEngine by lazy { StreamingMathRecognitionEngine(multimodalRecognition) }
    private val mathematics by lazy { MathematicsSubjectHandler(handwritingProvider) }
    private val physics by lazy { PhysicsSmartBoardSubjectHandler(handwritingProvider) }
    private val chemistry by lazy { Phase1SubjectRecognitionHandler(SmartBoardSubject.CHEMISTRY, handwritingProvider) }
    private val biology by lazy { Phase1SubjectRecognitionHandler(SmartBoardSubject.BIOLOGY, handwritingProvider) }
    private val subjectRegistry by lazy { SmartBoardSubjectRegistry(listOf(mathematics, physics, chemistry, biology)) }
    private val multiSubjectCapabilities by lazy {
        DefaultSmartBoardSubjectCapabilityRegistry(
            mapOf(
                SmartBoardSubject.MATHEMATICS to { mathematics },
                SmartBoardSubject.PHYSICS to { physics },
                SmartBoardSubject.CHEMISTRY to { chemistry },
                SmartBoardSubject.BIOLOGY to { biology },
            ),
        )
    }
    private val subjectDetector = DeterministicSmartBoardSubjectDetector()
    private val multiSubjectAnalytics = BoundedLocalSmartBoardMultiSubjectAnalytics()
    private val recognitionDiagnostics = BoundedRecognitionDiagnostics()
    private val unifiedRecognition by lazy {
        DefaultSmartBoardRecognitionOrchestrator(handwritingProvider, subjectDetector, multiSubjectCapabilities)
    }
    private val physicsIntelligence by lazy { PhysicsSmartBoardIntelligenceHandler() }
    private val physicsPhotoRecognition by lazy { PhysicsPhotoRecognitionAdapter() }
    private val intelligenceMemory = SmartBoardSessionMemoryManager()
    private val intelligenceAnalytics = BoundedLocalSmartBoardIntelligenceAnalytics()
    private val subjectIntelligence by lazy {
        SmartBoardSubjectIntelligenceRegistry(
            listOf(MathematicsSmartBoardIntelligenceHandler(), PhysicsSubjectIntelligenceAdapter()),
        )
    }
    private val intelligenceTools by lazy { DefaultSmartBoardToolRegistry(subjectIntelligence) }
    private val intelligenceOrchestrator by lazy {
        DefaultSmartBoardIntelligenceOrchestrator(
            subjectIntelligence,
            intelligenceTools,
            memory = intelligenceMemory,
        )
    }
    private val intelligenceContextBuilder by lazy {
        DefaultSmartBoardContextBuilder(
            memoryProvider = intelligenceMemory::get,
            serviceAvailability = {
                com.indianservers.smartboard.smartboard.intelligence.SmartBoardServiceAvailability(
                    com.indianservers.smartboard.smartboard.intelligence.SmartBoardIntelligenceLevel.DETERMINISTIC,
                    recognitionAvailable = true,
                    aiAvailable = false,
                )
            },
            deviceContext = {
                com.indianservers.smartboard.smartboard.intelligence.SmartBoardDeviceContext(
                    "responsive", preferences.reducedMotion, preferences.highContrast, networkAvailable = false,
                )
            },
            intelligenceMode = { preferences.intelligenceMode },
        )
    }
    private val casAdapter by lazy { SmartBoardCasAdapter() }
    private val imageAssets by lazy { SmartBoardImageAssetStore(application) }
    private val exporter by lazy { SmartBoardExporter(application) }
    private val autoShapeRecognizer by lazy { DeterministicAutoShapeRecognizer() }
    private val tutor by lazy { SmartBoardTutorEngine() }
    private val unifiedTutor by lazy { DefaultUnifiedSmartBoardTutor() }
    private val tutorContextBuilder = SmartBoardTutorContextBuilder()
    private val tutorAnalytics = com.indianservers.smartboard.smartboard.tutor.BoundedLocalSmartBoardTutorAnalytics()
    private var autosaveJob: Job? = null
    private var recognitionJob: Job? = null
    private var automaticRecognitionJob: Job? = null
    private var streamingRecognitionJob: Job? = null
    private var autoShapeJob: Job? = null
    private var intelligenceJob: Job? = null
    private var tutorJob: Job? = null
    private var classroomTimerJob: Job? = null
    private var lastRecognitionFingerprint: String? = null

    var document by mutableStateOf(
        SmartBoardDocument.new(
            id = savedStateHandle["smartBoardDocumentId"] ?: UUID.randomUUID().toString(),
            now = System.currentTimeMillis(),
            subject = SmartBoardSubject.MATHEMATICS,
        ),
    )
        private set
    var selectedIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var activeTool by mutableStateOf(SmartBoardTool.PEN)
        private set
    var preferences by mutableStateOf(SmartBoardPreferences())
        private set
    var recentBoards by mutableStateOf<List<SmartBoardDocument>>(emptyList())
        private set
    var recognitionReview by mutableStateOf<SmartBoardRecognitionReview?>(null)
        private set
    var recognitionTarget by mutableStateOf(SmartBoardRecognitionTarget.CONTENT)
        private set
    var pendingGraphLaunch by mutableStateOf<SmartBoardGraphLaunch?>(null)
        private set
    var shapeSuggestion by mutableStateOf<AutoShapeSuggestion?>(null)
        private set
    var streamingRecognitionSuggestion by mutableStateOf<SmartBoardStreamingRecognitionSuggestion?>(null)
        private set
    var correctionGestureSuggestion by mutableStateOf<CorrectionGestureSuggestion?>(null)
        private set
    var recognitionPersonalizationProfile by mutableStateOf(RecognitionPersonalizationProfile.Empty)
        private set
    var offlineMathModelStatus by mutableStateOf(offlineMathModelPack.status())
        private set
    var selectedSemanticNodeId by mutableStateOf<String?>(null)
        private set
    var classroomTimerRemainingSeconds by mutableStateOf(300)
        private set
    var classroomTimerRunning by mutableStateOf(false)
        private set
    var wholeBoardUnderstanding by mutableStateOf<SmartBoardWholeBoardUnderstanding?>(null)
        private set
    var recognizing by mutableStateOf(false)
        private set
    var runningAction by mutableStateOf(false)
        private set
    var status by mutableStateOf("Smart Board ready")
        private set
    var initialized by mutableStateOf(false)
        private set
    var intelligenceContext by mutableStateOf<SmartBoardIntelligenceContext?>(null)
        private set
    var intelligenceUnderstanding by mutableStateOf<SmartBoardUnderstandingResult?>(null)
        private set
    var intelligenceRecommendations by mutableStateOf<List<SmartBoardRecommendation>>(emptyList())
        private set
    var activeIntelligenceWorkflow by mutableStateOf<SmartBoardWorkflowPlan?>(null)
        private set
    var intelligenceBusy by mutableStateOf(false)
        private set
    var tutorContext by mutableStateOf<SmartBoardTutorContext?>(null)
        private set
    var tutorConversation by mutableStateOf(SmartBoardTutorConversation.empty(document.id))
        private set
    var tutorSuggestedPrompts by mutableStateOf<List<SmartBoardSuggestedPrompt>>(emptyList())
        private set
    var tutorLastResponse by mutableStateOf<UnifiedTutorResponse?>(null)
        private set
    var tutorBusy by mutableStateOf(false)
        private set

    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo
    val selectedElements get() = document.elements.filter { it.id in selectedIds }
    val selectedExpression: MathExpressionElement? get() = selectedElements.filterIsInstance<MathExpressionElement>().singleOrNull()
    val selectedGraph: GraphConfigurationElement? get() = selectedElements.filterIsInstance<GraphConfigurationElement>().singleOrNull()
    val selectedShape: ShapeElement? get() = selectedElements.filterIsInstance<ShapeElement>().singleOrNull()
    val selectedTable: TableElement? get() = selectedElements.filterIsInstance<TableElement>().singleOrNull()
    val selectedText: TextElement? get() = selectedElements.filterIsInstance<TextElement>().singleOrNull()
    val selectedPhysicsExpression: PhysicsExpressionElement? get() = selectedElements.filterIsInstance<PhysicsExpressionElement>().singleOrNull()
    val selectedSubjectComposition: Map<SmartBoardSubject, Int>
        get() = selectedElements.mapNotNull { element ->
            document.elementSubjectClassifications[element.id]?.primarySubject ?: when (element) {
                is MathExpressionElement -> SmartBoardSubject.MATHEMATICS
                is PhysicsExpressionElement, is PhysicsDiagramElement, is com.indianservers.smartboard.smartboard.models.PhysicsResultElement -> SmartBoardSubject.PHYSICS
                is ChemistryExpressionElement -> SmartBoardSubject.CHEMISTRY
                is EnglishTextElement -> SmartBoardSubject.ENGLISH
                is BiologyContentElement -> SmartBoardSubject.BIOLOGY
                else -> null
            }
        }.groupingBy { it }.eachCount()
    val selectedPhysicsActions: List<PhysicsActionType>
        get() = selectedPhysicsExpression?.let(physicsIntelligence::analyze)?.suggestedActions.orEmpty()
            .plus(listOf(PhysicsActionType.VERIFY_WORK, PhysicsActionType.TUTOR_HINT, PhysicsActionType.NEXT_STEP))
            .distinct()
    val selectedExpressionAnalysis: SmartBoardExpressionAnalysis?
        get() = selectedExpression?.let { SmartBoardExpressionAnalyzer.analyze(it.displayLatex) }
    val recognitionRuntimeHealth get() = recognitionDiagnostics.health()
    val semanticToolTargets: List<SemanticToolTarget>
        get() = selectedExpression?.semanticTree?.let(SmartBoardSemanticToolEngine::targets).orEmpty()

    init {
        viewModelScope.launch {
            preferences = runCatching { repository.loadPreferences() }.getOrDefault(SmartBoardPreferences())
            recognitionPersonalizationProfile = runCatching {
                RecognitionPersonalizationProfileCodec.decode(repository.loadRecognitionPersonalization())
            }.getOrDefault(RecognitionPersonalizationProfile.Empty)
            recentBoards = runCatching { repository.recent() }.getOrDefault(emptyList())
            val requestedId = savedStateHandle.get<String>("smartBoardDocumentId")
            val restored = requestedId?.let { runCatching { repository.load(it) }.getOrNull() }
                ?: runCatching { repository.loadRecovery() }.getOrNull()
            if (restored != null) document = restored
            runCatching { repository.loadIntelligenceMemory(document.id) }.getOrNull()?.let {
                intelligenceMemory.put(it)
                activeIntelligenceWorkflow = it.activeWorkflow
            }
            tutorConversation = runCatching { repository.loadTutorConversation(document.id) }.getOrNull()
                ?: SmartBoardTutorConversation.empty(document.id, now())
            savedStateHandle["smartBoardDocumentId"] = document.id
            initialized = true
            status = if (restored == null) "New Mathematics board" else "Recovered ${document.title}"
            if (preferences.intelligenceMode != com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode.MANUAL) {
                refreshIntelligence()
            }
        }
    }

    fun setTool(value: SmartBoardTool) {
        if (value in setOf(SmartBoardTool.LASSO, SmartBoardTool.RECTANGLE_SELECT, SmartBoardTool.ERASER)) {
            automaticRecognitionJob?.cancel()
            streamingRecognitionJob?.cancel()
            autoShapeJob?.cancel()
        }
        activeTool = value
        val resolvedInputMode = preferences.inputMode.afterSelecting(value)
        val enablesDirectTouch = resolvedInputMode != preferences.inputMode
        if (enablesDirectTouch) {
            updatePreferences(preferences.copy(inputMode = resolvedInputMode))
        }
        val toolName = value.name.lowercase().replace('_', ' ')
        status = when {
            enablesDirectTouch -> "$toolName selected · touch and stylus enabled"
            value.usesDirectPointerInput() && preferences.inputMode == SmartBoardInputMode.STYLUS_ONLY ->
                "$toolName selected · stylus-only mode"
            else -> "$toolName selected"
        }
    }

    fun selectSemanticNode(nodeId: String?) {
        selectedSemanticNodeId = nodeId
        status = nodeId?.let { "Subexpression selected" } ?: "Subexpression selection cleared"
    }

    fun applySemanticTool(operation: SemanticToolOperation) {
        val expression = selectedExpression ?: run {
            status = "Select one semantic Mathematics expression"
            return
        }
        val tree = expression.semanticTree ?: run {
            status = "This expression has no semantic structure yet"
            return
        }
        val nodeId = selectedSemanticNodeId ?: tree.root.id
        SmartBoardSemanticToolEngine.apply(tree, nodeId, operation)
            .onSuccess { result ->
                execute(
                    EditMathExpressionCommand(
                        expression,
                        expression.copy(
                            correctedLatex = result.expressionAfter,
                            normalizedExpression = result.expressionAfter,
                            semanticTree = result.tree,
                        ),
                    ),
                )
                selectedSemanticNodeId = null
                status = "${operation.name.lowercase()} applied directly; Undo restores the expression"
            }
            .onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
    }

    fun reconstructSelectedTable() {
        val expression = selectedExpression ?: run {
            status = "Select a recognized matrix first"
            return
        }
        SmartBoardEditableReconstructionEngine.tableFrom(expression, "table-${UUID.randomUUID()}", now())
            .onSuccess { table ->
                execute(AddElementCommand(table))
                selectedIds = setOf(table.id)
                status = "Editable table reconstructed and linked to the expression"
            }
            .onFailure { status = it.message ?: "This expression cannot be reconstructed as a table" }
    }

    fun updateSelectedTableCell(row: Int, column: Int, value: String) {
        val table = selectedTable ?: return
        if (row !in table.rows.indices || column !in table.columnHeaders.indices || value.length > 2_000) return
        val rows = table.rows.mapIndexed { rowIndex, cells ->
            if (rowIndex != row) cells else cells.mapIndexed { columnIndex, cell -> if (columnIndex == column) value else cell }
        }
        execute(ReplaceElementCommand(table, table.copy(rows = rows), "Edit table cell"))
        status = "Table cell updated"
    }

    fun updateSelectedTableHeader(column: Int, value: String) {
        val table = selectedTable ?: return
        if (column !in table.columnHeaders.indices || value.length > 200) return
        val headers = table.columnHeaders.mapIndexed { index, header -> if (index == column) value else header }
        execute(ReplaceElementCommand(table, table.copy(columnHeaders = headers), "Edit table header"))
        status = "Table header updated"
    }

    fun addSelectedTableRow() {
        val table = selectedTable ?: return
        if (table.rows.size >= 10_000) return
        execute(
            ReplaceElementCommand(
                table,
                table.copy(rows = table.rows + listOf(List(table.columnHeaders.size) { "" })),
                "Add table row",
            ),
        )
        status = "Table row added"
    }

    fun addSelectedTableColumn() {
        val table = selectedTable ?: return
        if (table.columnHeaders.size >= 64) return
        val updated = table.copy(
            columnHeaders = table.columnHeaders + "Column ${table.columnHeaders.size + 1}",
            rows = table.rows.map { it + "" },
        )
        execute(ReplaceElementCommand(table, updated, "Add table column"))
        status = "Table column added"
    }

    fun removeSelectedTableLastRow() {
        val table = selectedTable ?: return
        if (table.rows.isEmpty()) return
        execute(ReplaceElementCommand(table, table.copy(rows = table.rows.dropLast(1)), "Remove table row"))
        status = "Last table row removed"
    }

    fun removeSelectedTableLastColumn() {
        val table = selectedTable ?: return
        if (table.columnHeaders.size <= 1) return
        val updated = table.copy(
            columnHeaders = table.columnHeaders.dropLast(1),
            rows = table.rows.map { it.dropLast(1) },
        )
        execute(ReplaceElementCommand(table, updated, "Remove table column"))
        status = "Last table column removed"
    }

    fun insertStickyNote(text: String = "Add note…") {
        val safeText = text.trim().takeIf { it.isNotBlank() && it.length <= 8_000 } ?: return
        val bounds = insertionBounds(260f, 150f)
        val note = SmartBoardClassroomToolFactory.stickyNote(bounds, now(), safeText)
        execute(AddElementCommand(note))
        selectedIds = setOf(note.id)
        status = "Editable note inserted"
    }

    fun updateSelectedText(text: String) {
        val selected = selectedText ?: return
        val safeText = text.trim().takeIf { it.isNotBlank() && it.length <= 8_000 } ?: return
        execute(ReplaceElementCommand(selected, selected.copy(text = safeText), "Edit note"))
        status = "Note updated"
    }

    fun insertBlankTable() {
        val table = SmartBoardClassroomToolFactory.blankTable(insertionBounds(390f, 220f), now())
        execute(AddElementCommand(table))
        selectedIds = setOf(table.id)
        status = "Editable table inserted"
    }

    fun insertQuickShape(type: SmartBoardShapeType) {
        val size = when (type) {
            SmartBoardShapeType.COORDINATE_AXES -> 300f to 220f
            SmartBoardShapeType.NUMBER_LINE -> 320f to 70f
            else -> 180f to 140f
        }
        val shape = SmartBoardClassroomToolFactory.shape(type, insertionBounds(size.first, size.second), now())
        execute(AddElementCommand(shape))
        selectedIds = setOf(shape.id)
        status = "${type.name.lowercase().replace('_', ' ')} inserted"
    }

    fun startOrPauseClassroomTimer() {
        if (classroomTimerRunning) {
            classroomTimerJob?.cancel()
            classroomTimerRunning = false
            status = "Class timer paused"
            return
        }
        if (classroomTimerRemainingSeconds <= 0) classroomTimerRemainingSeconds = 300
        classroomTimerRunning = true
        classroomTimerJob = viewModelScope.launch {
            while (classroomTimerRunning && classroomTimerRemainingSeconds > 0) {
                delay(1_000)
                classroomTimerRemainingSeconds = (classroomTimerRemainingSeconds - 1).coerceAtLeast(0)
            }
            classroomTimerRunning = false
            if (classroomTimerRemainingSeconds == 0) status = "Class timer complete"
        }
        status = "Class timer running"
    }

    fun resetClassroomTimer(seconds: Int = 300) {
        classroomTimerJob?.cancel()
        classroomTimerRunning = false
        classroomTimerRemainingSeconds = seconds.coerceIn(30, 7_200)
        status = "Class timer reset"
    }

    fun select(ids: Set<String>) {
        selectedIds = SmartBoardSelection.groupedSelection(ids, document.relationships).filterTo(linkedSetOf()) { id -> document.elements.any { it.id == id } }
        selectedSemanticNodeId = null
        status = if (selectedIds.isEmpty()) "Selection cleared" else "${selectedIds.size} element(s) selected"
        scheduleIntelligence()
        refreshTutorContext()
    }

    fun addStroke(stroke: StrokeElement) {
        automaticRecognitionJob?.cancel()
        streamingRecognitionJob?.cancel()
        autoShapeJob?.cancel()
        shapeSuggestion = null
        val existingStrokes = document.elements.filterIsInstance<StrokeElement>().filterNot(StrokeElement::hidden)
        execute(AddElementCommand(stroke))
        correctionGestureSuggestion = SmartBoardCorrectionGestureDetector.detect(stroke, existingStrokes)
        selectedIds = emptySet()
        status = if (correctionGestureSuggestion == null) "Stroke added" else "Correction gesture detected; confirm before erasing"
        if (preferences.autoShapeEnabled && correctionGestureSuggestion == null) {
            autoShapeJob = viewModelScope.launch {
                delay(preferences.autoShapeDelayMillis.toLong())
                val strokes = document.elements.filterIsInstance<StrokeElement>()
                val related = SmartBoardStrokeGrouper.recentRelated(strokes, stroke)
                val candidates = withContext(Dispatchers.Default) { autoShapeRecognizer.recognize(related) }
                if (candidates.isNotEmpty() && candidates.all { candidate ->
                        candidate.sourceStrokeIds.all { id -> document.elements.any { it.id == id && !it.hidden } }
                    }) {
                    shapeSuggestion = AutoShapeSuggestion(candidates, createdAt = now(), forced = false)
                }
            }
        }
        if (preferences.recognitionMode == SmartBoardRecognitionMode.AUTOMATIC &&
            document.subjectMode.selection == SmartBoardSubject.MATHEMATICS
        ) {
            streamingRecognitionJob = viewModelScope.launch {
                // Wait for an actual writing pause. Starting formula vision after every individual
                // pen stroke interrupts multi-stroke equations and wastes on-device inference.
                delay(900)
                val current = document.elements.filterIsInstance<StrokeElement>().filterNot(StrokeElement::hidden)
                val related = SmartBoardStrokeGrouper.recentRelated(
                    current,
                    stroke,
                    maximumGapMillis = 5_000,
                    maximumStrokes = 48,
                )
                if (related.isEmpty() || correctionGestureSuggestion?.gestureStrokeId == stroke.id) return@launch
                runCatching {
                    val draft = MathRecognitionRequestBuilder.build(
                        document.id,
                        related,
                        now(),
                        subject = SmartBoardSubject.MATHEMATICS,
                    )
                    val png = withContext(Dispatchers.Default) { MathRecognitionInputRenderer.render(related, draft.bounds) }
                    val input = draft.copy(rasterPng = png)
                    val recognitionInput = com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput(
                        related,
                        draft.bounds,
                        png,
                        MathRecognitionRequestBuilder.fingerprint(draft),
                    )
                    val raw = streamingRecognitionEngine.update(recognitionInput, streamingRecognitionSuggestion?.snapshot)
                    input to contextualize(raw, draft.bounds)
                }.onSuccess { (input, outcome) ->
                    val snapshot = outcome.snapshot
                    streamingRecognitionSuggestion = SmartBoardStreamingRecognitionSuggestion(
                        input.copy(rasterPng = byteArrayOf()),
                        snapshot,
                        outcome.rankedCandidates.associate { it.candidate.text to it.evidence },
                    )
                    recordRecognitionDiagnostic(snapshot, RecognitionDiagnosticInput.FUSED)
                }.onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) return@onFailure
                }
            }
        } else if (preferences.recognitionMode != SmartBoardRecognitionMode.MANUAL_ONLY) {
            automaticRecognitionJob = viewModelScope.launch {
                delay(if (preferences.recognitionMode == SmartBoardRecognitionMode.AUTOMATIC) 900 else 1_500)
                if (correctionGestureSuggestion?.gestureStrokeId == stroke.id) return@launch
                val current = document.elements.filterIsInstance<StrokeElement>().filterNot(StrokeElement::hidden)
                val related = SmartBoardStrokeGrouper.recentRelated(
                    current,
                    stroke,
                    maximumGapMillis = 5_000,
                    maximumStrokes = 48,
                )
                if (related.isEmpty()) return@launch
                recognizeSelection(
                    strokeIds = related.mapTo(linkedSetOf(), StrokeElement::id),
                    silent = true,
                )
            }
        }
        scheduleIntelligence()
    }

    fun chooseStreamingCandidate(index: Int) {
        val suggestion = streamingRecognitionSuggestion ?: return
        val candidate = suggestion.snapshot.candidates.getOrNull(index) ?: return
        val result = suggestion.snapshot.result.copy(
            latex = candidate.text,
            normalizedExpression = candidate.normalizedExpression,
            plainText = candidate.text,
            confidence = candidate.confidence,
            detectedType = candidate.detectedType,
            alternatives = suggestion.snapshot.candidates.filterIndexed { candidateIndex, _ -> candidateIndex != index }
                .map { MathRecognitionAlternative(it.text, it.confidence) },
        )
        val semanticTree = SmartBoardSemanticExpressionBuilder.build(
            candidate.text,
            candidate.normalizedExpression,
            suggestion.input.strokeIds,
            candidate.confidence,
        )
        recognitionReview = SmartBoardRecognitionReview(
            input = suggestion.input,
            result = result,
            editableLatex = candidate.text,
            selectedSubject = SmartBoardSubject.MATHEMATICS,
            semanticTree = semanticTree,
            specialistInterpretations = SmartBoardSpecialistRecognitionRegistry.recognize(
                candidate.text,
                SmartBoardSubject.MATHEMATICS,
                semanticTree,
                nearbyShapes(suggestion.input.bounds),
            ),
            contextEvidence = suggestion.contextEvidenceByCandidate[candidate.text].orEmpty(),
        )
        selectedIds = suggestion.input.strokeIds.toSet()
        streamingRecognitionSuggestion = null
        status = "Live candidate opened for review; source handwriting is preserved"
    }

    fun dismissStreamingRecognition() {
        streamingRecognitionSuggestion = null
        status = "Live recognition dismissed; handwriting preserved"
    }

    fun acceptCorrectionGesture() {
        val suggestion = correctionGestureSuggestion ?: return
        val ids = suggestion.targetStrokeIds + suggestion.gestureStrokeId
        selectedIds = ids
        correctionGestureSuggestion = null
        deleteSelection()
        status = "${suggestion.type.name.lowercase().replace('_', ' ')} applied; Undo restores every stroke"
    }

    fun keepCorrectionGesture() {
        correctionGestureSuggestion = null
        status = "Gesture kept as ordinary ink"
    }

    fun recognizeShapeSelection() {
        val strokes = selectedElements.filterIsInstance<StrokeElement>()
        if (strokes.isEmpty()) {
            status = "Select handwriting strokes to recognize as a shape"
            return
        }
        autoShapeJob?.cancel()
        autoShapeJob = viewModelScope.launch {
            status = "Fitting selected strokes locally"
            val candidates = withContext(Dispatchers.Default) { autoShapeRecognizer.recognize(strokes, forced = true) }
            shapeSuggestion = candidates.takeIf { it.isNotEmpty() }?.let {
                AutoShapeSuggestion(candidates, createdAt = now(), forced = true)
            }
            status = if (shapeSuggestion == null) {
                "No reliable shape fit; original handwriting kept"
            } else {
                "Choose a shape candidate; no ink has been replaced"
            }
        }
    }

    fun chooseShapeCandidate(index: Int) {
        shapeSuggestion = shapeSuggestion?.select(index)
    }

    fun dismissShapeSuggestion() {
        shapeSuggestion = null
        status = "Shape suggestion dismissed; original handwriting kept"
    }

    fun acceptShapeSuggestion() {
        val candidate = shapeSuggestion?.selected ?: return
        val sources = document.elements.filterIsInstance<StrokeElement>().filter { it.id in candidate.sourceStrokeIds }
        if (sources.size != candidate.sourceStrokeIds.size) {
            shapeSuggestion = null
            status = "The source ink changed; recognize the shape again"
            return
        }
        val style = sources.first()
        val shape = candidate.toElement(
            id = "shape-${UUID.randomUUID()}",
            now = now(),
            strokeWidth = style.width,
            argbColor = style.argbColor,
            opacity = style.opacity,
        )
        val relationship = SmartBoardRelationship(
            "shape-source-${UUID.randomUUID()}",
            SmartBoardRelationshipType.RECOGNIZED_FROM,
            listOf(shape.id) + candidate.sourceStrokeIds,
            now(),
        )
        val inferredSubject = when (shape.shapeType) {
            SmartBoardShapeType.COORDINATE_AXES, SmartBoardShapeType.NUMBER_LINE,
            SmartBoardShapeType.ANGLE, SmartBoardShapeType.RIGHT_ANGLE_MARKER,
            SmartBoardShapeType.PARALLEL_LINES, SmartBoardShapeType.PERPENDICULAR_LINES ->
                SmartBoardSubject.MATHEMATICS
            SmartBoardShapeType.FORCE_ARROW, SmartBoardShapeType.SPRING,
            SmartBoardShapeType.RESISTOR, SmartBoardShapeType.CIRCUIT_WIRE ->
                SmartBoardSubject.PHYSICS
            SmartBoardShapeType.LAB_CONTAINER -> SmartBoardSubject.CHEMISTRY
            else -> document.subjectMode.selection.takeUnless {
                it in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.GENERAL)
            }
        }
        val classifications = inferredSubject?.let { subject ->
            mapOf(
                shape.id to SmartBoardSubjectClassification(
                    subject,
                    emptyList(),
                    shape.recognitionConfidence,
                    SubjectClassificationSource.LOCAL_RULES,
                    userConfirmed = false,
                    inheritedFromBoardMode = subject == document.subjectMode.selection,
                    warnings = emptyList(),
                ),
            )
        }.orEmpty()
        execute(
            InsertRecognizedExpressionCommand(
                expression = shape,
                relationship = relationship,
                sourceHiddenBefore = sources.associate { it.id to it.hidden },
                hideSources = true,
                classifications = classifications,
            ),
        )
        selectedIds = setOf(shape.id)
        shapeSuggestion = null
        status = "${shape.shapeType.name.lowercase().replace('_', ' ')} converted; Undo restores original ink"
        scheduleIntelligence()
        refreshTutorContext()
    }

    fun erase(point: SmartBoardPoint, tolerance: Float) {
        val stroke = document.elements.asReversed().filterIsInstance<StrokeElement>()
            .firstOrNull { !it.hidden && SmartBoardStrokeGeometry.distanceToStroke(point, it) <= tolerance + it.width / 2f }
            ?: return
        delete(setOf(stroke.id))
        status = "Stroke erased"
    }

    fun moveSelection(delta: SmartBoardPoint) {
        if (selectedIds.isEmpty()) return
        val movable = selectedIds.filterTo(linkedSetOf()) { id ->
            (document.elements.firstOrNull { it.id == id } as? ShapeElement)?.locked != true
        }
        if (movable.isEmpty()) {
            status = "Selected shape is locked"
            return
        }
        execute(MoveElementsCommand(movable, delta))
        status = if (movable.size == selectedIds.size) "Selection moved" else "Moved unlocked selection"
    }

    fun rotateSelectedShape() {
        val before = selectedShape ?: return
        if (before.locked) {
            status = "Unlock the shape before rotating it"
            return
        }
        val radians = 15.0 * PI / 180.0
        val centre = before.bounds.center
        val rotated = before.points.map { point ->
            val x = point.x - centre.x
            val y = point.y - centre.y
            SmartBoardPoint(
                centre.x + (x * cos(radians) - y * sin(radians)).toFloat(),
                centre.y + (x * sin(radians) + y * cos(radians)).toFloat(),
            )
        }
        execute(
            ReplaceElementCommand(
                before,
                before.copy(
                    points = rotated,
                    bounds = SmartBoardBounds.from(rotated),
                    rotationDegrees = (before.rotationDegrees + 15f) % 360f,
                ),
                "Rotate shape",
            ),
        )
        status = "Shape rotated 15 degrees"
    }

    fun toggleSelectedShapeLock() {
        val before = selectedShape ?: return
        execute(ReplaceElementCommand(before, before.copy(locked = !before.locked), "Change shape lock"))
        status = if (before.locked) "Shape unlocked" else "Shape locked"
    }

    fun cycleSelectedShapeStyle() {
        val before = selectedShape ?: return
        if (before.locked) {
            status = "Unlock the shape before styling it"
            return
        }
        val palette = listOf(0xFFF4F7FF, 0xFF43D9F5, 0xFF9A7BFF, 0xFFFFBF5A, 0xFF70E1A1)
        val next = palette[(palette.indexOf(before.argbColor).takeIf { it >= 0 } ?: 0).plus(1) % palette.size]
        execute(
            ReplaceElementCommand(
                before,
                before.copy(argbColor = next, strokeWidth = if (before.strokeWidth >= 5f) 2.5f else before.strokeWidth + .75f),
                "Style shape",
            ),
        )
        status = "Shape colour and stroke style updated"
    }

    fun deleteSelection() = delete(selectedIds)

    private fun delete(ids: Set<String>) {
        if (ids.isEmpty()) return
        val indexed = document.elements.withIndex().filter { it.value.id in ids }
        if (indexed.isEmpty()) return
        execute(
            DeleteElementsCommand(
                removed = indexed.map { it.value },
                originalIndices = indexed.map { it.index },
                affectedRelationships = document.relationships.filter { relationship -> relationship.elementIds.any(ids::contains) },
                removedClassifications = document.elementSubjectClassifications.filterKeys(ids::contains),
                removedConcepts = document.elementConcepts.filterKeys(ids::contains),
            ),
        )
        selectedIds = selectedIds - ids
    }

    fun duplicateSelection() {
        if (selectedIds.isEmpty()) return
        val copies = duplicateElements(document, selectedIds, { "element-${UUID.randomUUID()}" }, now())
        execute(AddElementsCommand(copies))
        selectedIds = copies.mapTo(linkedSetOf(), SmartBoardElement::id)
        status = "Selection duplicated"
    }

    fun groupSelection() {
        if (selectedIds.size < 2) return
        execute(GroupCommand(groupRelationship(selectedIds, "group-${UUID.randomUUID()}", now())))
        status = "Selection grouped"
    }

    fun ungroupSelection() {
        val groups = document.relationships.filter { it.type == SmartBoardRelationshipType.GROUP && it.elementIds.any(selectedIds::contains) }
        if (groups.isEmpty()) return
        execute(UngroupCommand(groups))
        status = "Selection ungrouped"
    }

    fun bringForward() = reorderSelection(forward = true)
    fun sendBackward() = reorderSelection(forward = false)

    private fun reorderSelection(forward: Boolean) {
        if (selectedIds.isEmpty()) return
        val before = document.elements.map(SmartBoardElement::id)
        val selected = before.filter(selectedIds::contains)
        val rest = before.filterNot(selectedIds::contains)
        val after = if (forward) rest + selected else selected + rest
        execute(ReorderElementsCommand(before, after))
        status = if (forward) "Selection brought forward" else "Selection sent backward"
    }

    fun clearBoard() {
        if (document.elements.isEmpty()) return
        execute(
            ClearBoardCommand(
                document.elements,
                document.relationships,
                document.elementSubjectClassifications,
                document.elementConcepts,
            ),
        )
        selectedIds = emptySet()
        status = "Board cleared; Undo restores it"
    }

    fun undo() {
        automaticRecognitionJob?.cancel()
        streamingRecognitionJob?.cancel()
        autoShapeJob?.cancel()
        document = history.undo(document, now())
        selectedIds = selectedIds.filterTo(linkedSetOf()) { id -> document.elements.any { it.id == id } }
        scheduleAutosave()
        status = history.undoLabel?.let { "Undid change; next is $it" } ?: "Undo complete"
    }

    fun redo() {
        automaticRecognitionJob?.cancel()
        streamingRecognitionJob?.cancel()
        autoShapeJob?.cancel()
        document = history.redo(document, now())
        scheduleAutosave()
        status = history.redoLabel?.let { "Redid change; next is $it" } ?: "Redo complete"
    }

    fun updateViewport(value: SmartBoardViewport) {
        document = document.copy(viewport = value, updatedAt = now())
        scheduleAutosave()
    }

    fun resetZoom() = updateViewport(SmartBoardViewport())

    fun changeBackground(value: SmartBoardBackground) {
        if (document.background == value) return
        execute(BackgroundCommand(document.background, value))
    }

    fun updatePreferences(value: SmartBoardPreferences) {
        preferences = value
        viewModelScope.launch { runCatching { repository.savePreferences(value) } }
    }

    fun clearRecognitionPersonalization() {
        recognitionPersonalizationProfile = RecognitionPersonalizationProfile.Empty
        viewModelScope.launch { runCatching { repository.clearRecognitionPersonalization() } }
        status = "Local recognition corrections cleared"
    }

    fun clearRecognitionDiagnostics() {
        recognitionDiagnostics.clear()
        status = "Local recognition diagnostics cleared"
    }

    fun installOfflineMathRecognitionModel() {
        if (offlineMathModelStatus.state == OfflineMathModelState.INSTALLING) return
        viewModelScope.launch {
            offlineMathModelStatus = OfflineMathModelStatus(
                OfflineMathModelState.INSTALLING,
                offlineMathModelStatus.downloadedBytes,
                message = "Starting offline Image-to-LaTeX installation",
            )
            offlineMathModelPack.install { progress -> offlineMathModelStatus = progress }
                .onSuccess {
                    offlineMathModelStatus = offlineMathModelPack.status()
                    status = "Dedicated offline Image-to-LaTeX model is ready"
                }
                .onFailure {
                    offlineMathModelStatus = offlineMathModelPack.status().copy(message = it.message ?: "Installation failed")
                    status = "Offline mathematics model installation failed"
                }
        }
    }

    fun removeOfflineMathRecognitionModel() {
        if (offlineMathModelPack.remove()) {
            offlineMathModelStatus = offlineMathModelPack.status()
            status = "Offline Image-to-LaTeX model removed"
        }
    }

    fun setAutoShapeEnabled(enabled: Boolean) {
        if (!enabled) {
            autoShapeJob?.cancel()
            shapeSuggestion = null
        }
        updatePreferences(preferences.copy(autoShapeEnabled = enabled))
        status = if (enabled) "Auto-shape suggestions enabled" else "Auto-shape suggestions disabled"
    }

    fun setIntelligenceMode(value: com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode) {
        updatePreferences(preferences.copy(intelligenceMode = value))
        recordIntelligenceEvent(SmartBoardIntelligenceEventType.MODE_SELECTED)
        status = "${value.name.lowercase().replace('_', ' ')} intelligence mode"
        if (value != com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode.MANUAL) refreshIntelligence()
    }

    fun setInputMode(value: SmartBoardInputMode) = updatePreferences(preferences.copy(inputMode = value))

    fun importImage(uri: Uri) {
        viewModelScope.launch {
            status = "Importing image"
            runCatching { imageAssets.import(uri, now()) }
                .onSuccess { image ->
                    execute(AddElementCommand(image))
                    selectedIds = setOf(image.id)
                    status = "Image imported privately; metadata removed"
                }
                .onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
        }
    }

    fun rotateSelectedImage(clockwise: Boolean = true) {
        val before = selectedElements.filterIsInstance<ImageElement>().singleOrNull() ?: return
        viewModelScope.launch {
            runCatching { imageAssets.rotate(before, clockwise) }
                .onSuccess { after ->
                    execute(ReplaceElementCommand(before, after, "Rotate image"))
                    status = "Image rotated"
                }
                .onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
        }
    }

    fun recognizeSelectedPhysicsImage() {
        if (document.subject != SmartBoardSubject.PHYSICS) {
            status = "Photo Physics analysis is available on a Physics Board"
            return
        }
        val image = selectedElements.filterIsInstance<ImageElement>().singleOrNull() ?: run {
            status = "Select one imported image first"
            return
        }
        viewModelScope.launch {
            recognizing = true
            status = "Recognizing Physics content locally from the selected image"
            runCatching {
                val bytes = withContext(Dispatchers.IO) { imageAssets.resolve(image).readBytes() }
                physicsPhotoRecognition.recognize(bytes)
            }.onSuccess { (recognition, physicsAnalysis) ->
                val input = SmartBoardRecognitionInput(
                    document.id, SmartBoardSubject.PHYSICS, listOf(image.id), emptyList(), image.bounds,
                    requestedAt = now(),
                )
                recognitionReview = SmartBoardRecognitionReview(
                    input,
                    recognition,
                    recognition.latex,
                    subjectAnalysis = SmartBoardSubjectAnalysis(
                        SmartBoardSubject.PHYSICS,
                        "Physics content recognized locally from the selected image.",
                        recognition,
                        mapOf(
                            "contentType" to physicsAnalysis.contentType.name,
                            "topic" to (physicsAnalysis.topic?.name ?: ""),
                            "formulaId" to (physicsAnalysis.equations.firstOrNull()?.formulaId ?: ""),
                            "actions" to physicsAnalysis.suggestedActions.joinToString(",") { it.name },
                            "ambiguities" to physicsAnalysis.ambiguities.joinToString("\u001f") { it.message },
                            "warnings" to physicsAnalysis.warnings.joinToString("\u001f"),
                        ),
                    ),
                    selectedSubject = SmartBoardSubject.PHYSICS,
                    specialistInterpretations = SmartBoardSpecialistRecognitionRegistry.recognize(
                        recognition.latex,
                        SmartBoardSubject.PHYSICS,
                        nearbyShapes = nearbyShapes(image.bounds),
                    ),
                )
                status = "Photo recognition ready for confirmation"
            }.onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
            recognizing = false
        }
    }

    fun export(format: SmartBoardExportFormat, destination: Uri? = null) {
        viewModelScope.launch {
            status = "Exporting ${format.name.lowercase()}"
            runCatching {
                val exported = exporter.export(document, format)
                if (destination != null) {
                    getApplication<Application>().contentResolver.openOutputStream(destination, "w")?.use { output ->
                        exported.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("The selected destination could not be opened")
                }
                exported
            }.onSuccess {
                status = if (destination == null) "Export ready: ${it.name}" else "Exported ${it.name}"
            }
                .onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
        }
    }

    fun importBoard(source: Uri) {
        viewModelScope.launch {
            status = "Importing board document"
            runCatching {
                val payload = getApplication<Application>().contentResolver.openInputStream(source)?.bufferedReader()?.use { it.readText() }
                    ?: error("The selected document could not be opened")
                val decoded = SmartBoardDocumentCodec.decode(payload)
                decoded.document ?: error(decoded.warnings.joinToString().ifBlank { "Unsupported or corrupted board document" })
            }.onSuccess { imported ->
                autosaveJob?.cancel()
                autoShapeJob?.cancel()
                history.clear()
                selectedIds = emptySet()
                recognitionReview = null
                shapeSuggestion = null
                streamingRecognitionSuggestion = null
                correctionGestureSuggestion = null
                document = imported
                savedStateHandle["smartBoardDocumentId"] = imported.id
                repository.save(imported)
                repository.saveRecovery(imported)
                recentBoards = repository.recent()
                status = "Imported ${imported.title}"
            }.onFailure {
                status = "Import failed: ${SmartBoardSecurityPolicy.safeError(it)}"
            }
        }
    }

    fun rename(value: String) {
        val title = value.trim().take(80)
        if (title.isBlank()) return
        document = document.copy(title = title, updatedAt = now())
        scheduleAutosave()
        status = "Board renamed"
    }

    fun newBoard(subject: SmartBoardSubject = SmartBoardSubject.MATHEMATICS) {
        require(SmartBoardClassroomSubjects.supports(subject))
        history.clear()
        selectedIds = emptySet()
        recognitionReview = null
        shapeSuggestion = null
        streamingRecognitionSuggestion = null
        correctionGestureSuggestion = null
        document = SmartBoardDocument.new(
            UUID.randomUUID().toString(),
            now(),
            title = "Untitled Board",
            subject = subject,
        )
        savedStateHandle["smartBoardDocumentId"] = document.id
        scheduleAutosave()
        status = "New ${subject.displayName()} board"
        intelligenceContext = null
        intelligenceUnderstanding = null
        intelligenceRecommendations = emptyList()
        activeIntelligenceWorkflow = null
        tutorConversation = SmartBoardTutorConversation.empty(document.id, now())
        tutorContext = null
        tutorLastResponse = null
        tutorSuggestedPrompts = emptyList()
    }

    fun setBoardSubject(subject: SmartBoardSubject) {
        if (!SmartBoardClassroomSubjects.supports(subject)) return
        if (document.subjectMode.selection == subject) return
        val before = document.subjectMode
        val after = before.copy(selection = subject, userSelected = true, lastChangedAt = now())
        execute(ChangeBoardSubjectModeCommand(before, after))
        recordSubjectEvent(
            if (subject == SmartBoardSubject.AUTO) SmartBoardMultiSubjectEventType.AUTO_DETECT_USED
            else SmartBoardMultiSubjectEventType.SUBJECT_MODE_SELECTED,
            subject,
        )
        recognitionReview = null
        if (subject !in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.MATHEMATICS)) {
            recognitionTarget = SmartBoardRecognitionTarget.CONTENT
        }
        status = "Board mode changed to ${subject.displayName()}; existing content was preserved"
        refreshTutorContext()
    }

    fun updateRecognitionTarget(target: SmartBoardRecognitionTarget) {
        if (target != SmartBoardRecognitionTarget.CONTENT &&
            document.subjectMode.selection !in setOf(SmartBoardSubject.AUTO, SmartBoardSubject.MATHEMATICS)
        ) {
            status = "Graph recognition is available in Mathematics or Auto Detect mode"
            return
        }
        recognitionTarget = target
        status = when (target) {
            SmartBoardRecognitionTarget.GRAPH_2D ->
                "2D graph mode: recognized mathematical expressions will open as editable graphs"
            SmartBoardRecognitionTarget.GRAPH_3D ->
                "3D graph mode: recognized z=f(x,y) surfaces will open as editable 3D graphs"
            SmartBoardRecognitionTarget.CONTENT ->
                "Content mode: recognized writing will become subject-aware board content"
        }
    }

    fun consumePendingGraphLaunch() {
        pendingGraphLaunch = null
    }

    fun setSubjectLock(locked: Boolean) {
        val before = document.subjectMode
        if (before.locked == locked) return
        execute(ChangeBoardSubjectModeCommand(before, before.copy(locked = locked, lastChangedAt = now())))
        recordSubjectEvent(SmartBoardMultiSubjectEventType.SUBJECT_LOCK_CHANGED, before.selection)
        status = if (locked) "${before.selection.displayName()} subject locked" else "Subject lock removed"
    }

    fun assignSelectedSubject(subject: SmartBoardSubject) {
        if (selectedIds.isEmpty() || subject !in SmartBoardClassroomSubjects.academic) return
        val classification = SmartBoardSubjectClassification(
            subject, emptyList(), 1f, SubjectClassificationSource.USER_SELECTION,
            userConfirmed = true, inheritedFromBoardMode = false, warnings = emptyList(),
        )
        execute(
            AssignSubjectClassificationCommand(
                selectedIds,
                document.elementSubjectClassifications.filterKeys(selectedIds::contains),
                classification,
            ),
        )
        recordSubjectEvent(SmartBoardMultiSubjectEventType.DETECTION_CORRECTED, subject)
        status = "Selected content classified as ${subject.displayName()}"
    }

    fun save() {
        viewModelScope.launch {
            runCatching {
                repository.save(document)
                repository.saveRecovery(document)
                repository.saveTutorConversation(tutorConversation)
                recentBoards = repository.recent()
                imageAssets.cleanupUnreferenced(repository.referencedAssetIds())
            }.onSuccess { status = "Saved ${document.title}" }
                .onFailure { status = "Save failed: ${it.message ?: "storage error"}" }
        }
    }

    fun openBoard(id: String) {
        viewModelScope.launch {
            status = "Opening board…"
            val loaded = runCatching {
                // Preserve the current editable document before switching. This also makes an
                // explicit Open safe when an autosave is still waiting to run.
                if (document.id != id) {
                    repository.save(document)
                    repository.saveRecovery(document)
                }
                repository.load(id)
            }.getOrNull()
            if (loaded == null) {
                status = "Board could not be opened"
            } else {
                autosaveJob?.cancel()
                autoShapeJob?.cancel()
                document = loaded
                history.clear()
                selectedIds = emptySet()
                recognitionReview = null
                shapeSuggestion = null
                streamingRecognitionSuggestion = null
                correctionGestureSuggestion = null
                intelligenceContext = null
                intelligenceUnderstanding = null
                intelligenceRecommendations = emptyList()
                activeIntelligenceWorkflow = null
                savedStateHandle["smartBoardDocumentId"] = loaded.id
                recentBoards = runCatching { repository.recent() }.getOrDefault(recentBoards)
                status = "Opened ${loaded.title} — select any object to continue editing"
                runCatching { repository.loadIntelligenceMemory(loaded.id) }.getOrNull()?.let {
                    intelligenceMemory.put(it)
                    activeIntelligenceWorkflow = it.activeWorkflow
                }
                tutorConversation = runCatching { repository.loadTutorConversation(loaded.id) }.getOrNull()
                    ?: SmartBoardTutorConversation.empty(loaded.id, now())
                tutorLastResponse = null
                refreshTutorContext()
                refreshIntelligence()
            }
        }
    }

    fun deleteBoard(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            recentBoards = repository.recent()
            if (document.id == id) newBoard()
            status = "Board deleted"
        }
    }

    fun recognizeSelection(force: Boolean = false, strokeIds: Set<String>? = null, silent: Boolean = false) {
        automaticRecognitionJob?.cancel()
        streamingRecognitionJob?.cancel()
        autoShapeJob?.cancel()
        val strokes = when {
            strokeIds != null -> document.elements.filter { it.id in strokeIds }
            selectedIds.isEmpty() -> document.elements
            else -> selectedElements
        }
            .filterIsInstance<StrokeElement>()
            .filterNot(StrokeElement::hidden)
        if (strokes.isEmpty()) {
            if (!silent) status = "Select handwriting or draw an expression first"
            return
        }
        val draft = MathRecognitionRequestBuilder.build(document.id, strokes, now(), subject = document.subjectMode.selection)
        val fingerprint = MathRecognitionRequestBuilder.fingerprint(draft)
        if (!force && fingerprint == lastRecognitionFingerprint && recognitionReview != null) {
            if (!silent) status = "This handwriting is already in review"
            return
        }
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            recognizing = true
            if (!silent) status = "Preparing high-contrast recognition input"
            var contextualEvidence = emptyList<RecognitionRerankEvidence>()
            runCatching {
                val png = withContext(Dispatchers.Default) { MathRecognitionInputRenderer.render(strokes, draft.bounds) }
                val input = draft.copy(rasterPng = png)
                val unified = unifiedRecognition.recognize(UnifiedRecognitionRequest(input, document.subjectMode))
                val explicitFormulaVision = strokeIds == null || force
                if (explicitFormulaVision &&
                    (unified.routedSubject == SmartBoardSubject.MATHEMATICS ||
                        document.subjectMode.selection == SmartBoardSubject.MATHEMATICS)
                ) {
                    val fused = multimodalRecognition.enhanceWithRaster(
                        com.indianservers.smartboard.smartboard.recognition.MathRecognitionInput(
                            strokes,
                            draft.bounds,
                            png,
                            fingerprint,
                        ),
                        unified.recognition,
                        recognitionReview?.result?.latex,
                    )
                    val contextual = contextualize(fused, draft.bounds)
                    contextualEvidence = contextual.primaryEvidence
                    recordRecognitionDiagnostic(contextual.snapshot, RecognitionDiagnosticInput.FUSED)
                    unified.copy(
                        recognition = contextual.snapshot.result,
                        analysis = unified.analysis?.copy(recognition = contextual.snapshot.result),
                        providerId = "${unified.providerId}+${imageRecognitionProvider.id}+parser-context-rerank",
                    )
                } else {
                    unified
                }
            }.onSuccess { unified ->
                val result = unified.recognition
                val semanticTree = if (unified.routedSubject == SmartBoardSubject.MATHEMATICS) {
                    SmartBoardSemanticExpressionBuilder.build(
                        result.latex,
                        result.normalizedExpression,
                        draft.strokeIds,
                        result.confidence,
                    )
                } else null
                lastRecognitionFingerprint = fingerprint
                recognitionReview = SmartBoardRecognitionReview(
                    draft.copy(rasterPng = byteArrayOf()),
                    result,
                    result.latex,
                    subjectAnalysis = unified.analysis,
                    subjectDetection = unified.detection,
                    selectedSubject = unified.routedSubject,
                    semanticTree = semanticTree,
                    specialistInterpretations = SmartBoardSpecialistRecognitionRegistry.recognize(
                        result.latex,
                        unified.routedSubject,
                        semanticTree,
                        nearbyShapes(draft.bounds),
                    ),
                    contextEvidence = contextualEvidence,
                )
                recordSubjectEvent(
                    if (unified.routedSubject == null) SmartBoardMultiSubjectEventType.DETECTION_UNRESOLVED
                    else SmartBoardMultiSubjectEventType.SUBJECT_DETECTED,
                    unified.routedSubject,
                    unified.detection.confidenceLevel.name,
                    unified.detection.cacheHit,
                    latencyBucket = when (unified.detection.detectionLatencyMillis) {
                        in 0..49 -> "under_50ms"
                        in 50..199 -> "50_199ms"
                        else -> "200ms_plus"
                    },
                )
                if (!silent) {
                    status = when {
                        unified.detection.requiresConfirmation -> "Subject confirmation required; handwriting preserved"
                        unified.routedSubject != null -> "Detected subject: ${unified.routedSubject.displayName()}"
                        else -> "Subject unresolved; choose a subject to continue"
                    }
                }
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) return@onFailure
                if (!silent) status = "Recognition failed: ${error.message ?: "unknown error"}"
            }
            recognizing = false
        }
    }

    fun cancelRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        recognizing = false
        recognitionReview = null
        status = "Recognition cancelled; handwriting preserved"
    }

    fun editRecognitionLatex(value: String) {
        recognitionReview = recognitionReview?.let { review ->
            val semanticTree = if (review.selectedSubject == SmartBoardSubject.MATHEMATICS) {
                runCatching {
                    SmartBoardSemanticExpressionBuilder.build(
                        value,
                        sourceStrokeIds = review.input.strokeIds,
                        confidence = review.result.confidence,
                    )
                }.getOrNull()
            } else null
            review.copy(
                editableLatex = value,
                validationMessage = null,
                semanticTree = semanticTree,
                contextEvidence = emptyList(),
                specialistInterpretations = SmartBoardSpecialistRecognitionRegistry.recognize(
                    value,
                    review.selectedSubject,
                    semanticTree,
                    nearbyShapes(review.input.bounds),
                ),
            )
        }
    }

    fun analyzeWholeBoard() {
        wholeBoardUnderstanding = SmartBoardWholeBoardUnderstandingEngine.analyze(document, now())
        status = wholeBoardUnderstanding?.let {
            "${it.summary}; ${it.relationshipSuggestions.size} relationship suggestions ready for review"
        } ?: "Board understanding unavailable"
    }

    fun acceptWholeBoardRelationships() {
        val understanding = wholeBoardUnderstanding ?: return
        val relationships = understanding.relationshipSuggestions
            .filter { it.confidence >= .70f }
            .map { it.relationship }
        if (relationships.isEmpty()) {
            status = "No new high-confidence relationships to add"
            return
        }
        execute(AddRelationshipsCommand(relationships))
        wholeBoardUnderstanding = null
        status = "${relationships.size} board relationships added; Undo is available"
    }

    fun dismissWholeBoardUnderstanding() {
        wholeBoardUnderstanding = null
        status = "Board relationship suggestions dismissed; content unchanged"
    }

    fun chooseAlternative(value: String) = editRecognitionLatex(value)

    fun chooseRecognitionSubject(value: SmartBoardSubject) {
        if (value !in SmartBoardClassroomSubjects.academic) return
        recognitionReview = recognitionReview?.let { review ->
            val semanticTree = if (value == SmartBoardSubject.MATHEMATICS) {
                runCatching {
                    SmartBoardSemanticExpressionBuilder.build(
                        review.editableLatex,
                        sourceStrokeIds = review.input.strokeIds,
                        confidence = review.result.confidence,
                    )
                }.getOrNull()
            } else null
            review.copy(
                selectedSubject = value,
                validationMessage = null,
                semanticTree = semanticTree,
                specialistInterpretations = SmartBoardSpecialistRecognitionRegistry.recognize(
                    review.editableLatex,
                    value,
                    semanticTree,
                    nearbyShapes(review.input.bounds),
                ),
            )
        }
        recordSubjectEvent(SmartBoardMultiSubjectEventType.DETECTION_CORRECTED, value)
        status = "Use ${value.displayName()} for this content"
    }

    fun setHideSourceHandwriting(value: Boolean) {
        recognitionReview = recognitionReview?.copy(hideSourceHandwriting = value)
    }

    fun confirmRecognition() {
        val review = recognitionReview ?: return
        val lockedSubject = document.subjectMode.selection.takeIf {
            document.subjectMode.locked && it in SmartBoardClassroomSubjects.academic
        }
        val resolvedSubject = lockedSubject ?: sequenceOf(
            review.selectedSubject,
            review.subjectDetection?.primarySubject,
            document.subjectMode.selection,
        ).filterNotNull().firstOrNull { it in SmartBoardClassroomSubjects.academic } ?: run {
            recognitionReview = review.copy(validationMessage = "Choose Mathematics, Physics, Chemistry or Biology.")
            return
        }
        val latex = if (resolvedSubject in setOf(SmartBoardSubject.MATHEMATICS, SmartBoardSubject.PHYSICS, SmartBoardSubject.CHEMISTRY)) {
            SafeLatexPreview.validate(review.editableLatex).getOrElse { error ->
                recognitionReview = review.copy(validationMessage = error.message ?: "Invalid notation")
                return
            }
        } else {
            review.editableLatex.trim().takeIf { it.isNotBlank() && it.length <= 8_000 } ?: run {
                recognitionReview = review.copy(validationMessage = "Recognized text is empty or too long.")
                return
            }
        }
        val bounds = review.input.bounds.expand(8f)
        val physicsAnalysis = if (resolvedSubject == SmartBoardSubject.PHYSICS) {
            PhysicsBoardAnalyzer().analyze(latex, review.result.confidence)
        } else null
        val baseClassification = (review.subjectDetection?.classification(userConfirmed = true)
            ?: SmartBoardSubjectClassification(
                resolvedSubject, emptyList(), 1f, SubjectClassificationSource.USER_SELECTION,
                userConfirmed = true, inheritedFromBoardMode = false, warnings = emptyList(),
        )).copy(primarySubject = resolvedSubject, confidence = 1f, userConfirmed = true, source = SubjectClassificationSource.USER_SELECTION)
        val element: SmartBoardElement = if (resolvedSubject == SmartBoardSubject.PHYSICS) {
            val attributes = review.subjectAnalysis?.attributes.orEmpty()
            PhysicsExpressionElement(
                id = "physics-${UUID.randomUUID()}",
                rawSource = review.result.latex,
                correctedSource = latex.takeIf { it != review.result.latex },
                contentType = attributes["contentType"]?.let { runCatching { PhysicsContentType.valueOf(it) }.getOrNull() }
                    ?: requireNotNull(physicsAnalysis).contentType,
                topic = attributes["topic"]?.takeIf(String::isNotBlank)?.let { runCatching { PhysicsTopic.valueOf(it) }.getOrNull() },
                formulaId = attributes["formulaId"]?.takeIf(String::isNotBlank),
                sourceStrokeIds = review.input.strokeIds,
                recognitionConfidence = review.result.confidence,
                ambiguities = attributes["ambiguities"].orEmpty().split('\u001f').filter(String::isNotBlank),
                warnings = attributes["warnings"].orEmpty().split('\u001f').filter(String::isNotBlank),
                bounds = SmartBoardBounds(bounds.left, bounds.top, maxOf(bounds.right, bounds.left + 180f), maxOf(bounds.bottom, bounds.top + 52f)),
                createdAt = now(),
            )
        } else if (resolvedSubject == SmartBoardSubject.MATHEMATICS) {
            val semanticTree = SmartBoardSemanticExpressionBuilder.build(
                latex,
                review.result.normalizedExpression,
                review.input.strokeIds,
                review.result.confidence,
            )
            MathExpressionElement(
                id = "math-${UUID.randomUUID()}",
                rawLatex = review.result.latex,
                correctedLatex = latex.takeIf { it != review.result.latex },
                normalizedExpression = review.result.normalizedExpression,
                sourceStrokeIds = review.input.strokeIds,
                recognitionConfidence = review.result.confidence,
                bounds = SmartBoardBounds(bounds.left, bounds.top, maxOf(bounds.right, bounds.left + 120f), maxOf(bounds.bottom, bounds.top + 42f)),
                createdAt = now(),
                semanticTree = semanticTree,
            )
        } else if (resolvedSubject == SmartBoardSubject.CHEMISTRY) {
            ChemistryExpressionElement(
                "chemistry-${UUID.randomUUID()}", review.result.latex,
                normalizeChemistry(latex), chemistryType(latex), review.input.strokeIds,
                SmartBoardBounds(bounds.left, bounds.top, maxOf(bounds.right, bounds.left + 160f), maxOf(bounds.bottom, bounds.top + 46f)),
                now(), baseClassification,
            )
        } else if (resolvedSubject == SmartBoardSubject.ENGLISH) {
            EnglishTextElement(
                "english-${UUID.randomUUID()}", review.result.latex, latex.takeIf { it != review.result.latex },
                "en", englishType(latex), review.input.strokeIds, latex.indices.filter { latex[it] == '\n' },
                SmartBoardBounds(bounds.left, bounds.top, maxOf(bounds.right, bounds.left + 220f), maxOf(bounds.bottom, bounds.top + 56f)),
                now(), baseClassification,
            )
        } else {
            BiologyContentElement(
                "biology-${UUID.randomUUID()}", latex, biologyType(latex), emptyList(), review.input.strokeIds,
                SmartBoardBounds(bounds.left, bounds.top, maxOf(bounds.right, bounds.left + 200f), maxOf(bounds.bottom, bounds.top + 56f)),
                now(), baseClassification,
            )
        }
        val preparedGraph = if (
            resolvedSubject == SmartBoardSubject.MATHEMATICS &&
            recognitionTarget != SmartBoardRecognitionTarget.CONTENT
        ) {
            SmartBoardGraphAdapter.prepare(
                latex,
                threeDimensional = recognitionTarget == SmartBoardRecognitionTarget.GRAPH_3D,
            ).getOrElse { error ->
                recognitionReview = review.copy(
                    validationMessage = "This handwriting is not graphable yet: ${SmartBoardSecurityPolicy.safeError(error)}",
                )
                return
            }
        } else null
        val subjectRelatedElements = physicsAnalysis?.diagrams.orEmpty().mapIndexed { index, diagram ->
            PhysicsDiagramElement(
                id = "physics-diagram-${UUID.randomUUID()}",
                diagramType = diagram.type,
                sourceStrokeIds = review.input.strokeIds,
                detectedObjects = diagram.objects,
                confirmedRelations = diagram.confirmedRelations,
                inferredRelations = diagram.inferredRelations,
                confidence = diagram.confidence,
                bounds = element.bounds.translate(SmartBoardPoint(0f, element.bounds.height + 16f + index * 70f)),
                createdAt = now(),
            )
        }
        val graphElement = preparedGraph?.let { prepared ->
            GraphConfigurationElement(
                id = "graph-${UUID.randomUUID()}",
                graphKind = prepared.kind,
                expressions = listOf(prepared.expression),
                sourceElementIds = listOf(element.id) + review.input.strokeIds,
                moduleRoute = prepared.route,
                bounds = element.bounds.translate(SmartBoardPoint(0f, element.bounds.height + 20f)),
                createdAt = now(),
            )
        }
        val relatedElements = subjectRelatedElements + listOfNotNull(graphElement)
        val relationship = SmartBoardRelationship(
            "recognized-${UUID.randomUUID()}",
            SmartBoardRelationshipType.RECOGNIZED_FROM,
            listOf(element.id) + relatedElements.map(SmartBoardElement::id) + review.input.strokeIds,
            now(),
        )
        execute(
            InsertRecognizedExpressionCommand(
                expression = element,
                relationship = relationship,
                sourceHiddenBefore = document.elements.filterIsInstance<StrokeElement>()
                    .filter { it.id in review.input.strokeIds }
                    .associate { it.id to it.hidden },
                hideSources = review.hideSourceHandwriting,
                relatedElements = relatedElements,
                classifications = (listOf(element) + relatedElements).associate { it.id to baseClassification },
                concepts = review.subjectDetection?.detectedConcepts?.firstOrNull { it.subject == resolvedSubject }
                    ?.let { mapOf(element.id to it) }.orEmpty(),
            ),
        )
        if (resolvedSubject == SmartBoardSubject.MATHEMATICS) {
            if (preferences.recognitionPersonalizationEnabled) {
                val updatedProfile = SmartBoardRecognitionPersonalizer.recordCorrection(
                    recognitionPersonalizationProfile,
                    review.result.latex,
                    latex,
                    now(),
                )
                if (updatedProfile != recognitionPersonalizationProfile) {
                    recognitionPersonalizationProfile = updatedProfile
                    viewModelScope.launch {
                        runCatching {
                            repository.saveRecognitionPersonalization(
                                RecognitionPersonalizationProfileCodec.encode(updatedProfile),
                            )
                        }
                    }
                }
            }
            if (preferences.recognitionDiagnosticsEnabled) {
                val candidateTexts = listOf(review.result.latex) + review.result.alternatives.map(MathRecognitionAlternative::latex)
                val selectedRank = candidateTexts.indexOfFirst { it == latex }.takeIf { it >= 0 }?.plus(1)
                recognitionDiagnostics.record(
                    RecognitionDiagnosticEvent(
                        input = RecognitionDiagnosticInput.FUSED,
                        latency = RecognitionLatencyBucket.UNKNOWN,
                        confidence = confidenceBucket(review.result.confidence),
                        candidateCount = candidateTexts.size.coerceAtMost(16),
                        selectedRank = selectedRank,
                        corrected = review.result.latex != latex,
                        occurredAt = now(),
                    ),
                )
            }
        }
        selectedIds = setOf(graphElement?.id ?: element.id)
        recognitionReview = null
        if (preparedGraph != null) {
            pendingGraphLaunch = SmartBoardGraphLaunch(preparedGraph.route, preparedGraph.expression)
        }
        status = when {
            preparedGraph != null -> "Recognized expression inserted and opened as an editable graph"
            resolvedSubject == SmartBoardSubject.MATHEMATICS -> "Recognized expression inserted; source strokes preserved"
            resolvedSubject == SmartBoardSubject.PHYSICS -> "Recognized Physics expression inserted; source strokes preserved"
            else -> "Recognized ${resolvedSubject.displayName()} content inserted; source strokes preserved"
        }
    }

    fun editExpression(element: MathExpressionElement, latex: String) {
        val prepared = SmartBoardLatexAdapter.prepare(latex).getOrElse {
            status = it.message ?: "Invalid notation"
            return
        }
        execute(
            EditMathExpressionCommand(
                element,
                element.copy(
                    correctedLatex = prepared.latex,
                    normalizedExpression = prepared.engineExpression,
                    semanticTree = SmartBoardSemanticExpressionBuilder.build(
                        prepared.latex,
                        prepared.engineExpression,
                        element.sourceStrokeIds,
                        element.recognitionConfidence,
                    ),
                ),
            ),
        )
        status = "Expression updated"
    }

    fun insertOrUpdateLatex(latex: String, existing: MathExpressionElement? = selectedExpression): Boolean {
        val prepared = SmartBoardLatexAdapter.prepare(latex).getOrElse {
            status = it.message ?: "Invalid notation"
            return false
        }
        if (existing != null) {
            execute(
                EditMathExpressionCommand(
                    existing,
                    existing.copy(
                        correctedLatex = prepared.latex,
                        normalizedExpression = prepared.engineExpression,
                        semanticTree = SmartBoardSemanticExpressionBuilder.build(
                            prepared.latex,
                            prepared.engineExpression,
                            existing.sourceStrokeIds,
                            existing.recognitionConfidence,
                        ),
                    ),
                ),
            )
            selectedIds = setOf(existing.id)
            status = "LaTeX expression updated; board editing remains active"
            return true
        }
        val centerX = -document.viewport.panX / document.viewport.zoom + 72f
        val centerY = -document.viewport.panY / document.viewport.zoom + 96f
        val element = MathExpressionElement(
            id = "math-${UUID.randomUUID()}",
            rawLatex = prepared.latex,
            correctedLatex = null,
            normalizedExpression = prepared.engineExpression,
            sourceStrokeIds = emptyList(),
            recognitionConfidence = 1f,
            bounds = SmartBoardBounds(centerX, centerY, centerX + 340f, centerY + 96f),
            createdAt = now(),
            semanticTree = SmartBoardSemanticExpressionBuilder.build(prepared.latex, prepared.engineExpression),
        )
        execute(AddElementCommand(element))
        selectedIds = setOf(element.id)
        status = "LaTeX expression inserted as an editable board object"
        return true
    }

    fun insertOrUpdateGraph(
        expression: String,
        threeDimensional: Boolean = false,
        existing: GraphConfigurationElement? = selectedGraph,
    ): Boolean {
        val prepared = SmartBoardGraphAdapter.prepare(expression, threeDimensional).getOrElse {
            status = SmartBoardSecurityPolicy.safeError(it)
            return false
        }
        if (existing != null) {
            execute(
                ReplaceElementCommand(
                    existing,
                    existing.copy(
                        graphKind = prepared.kind,
                        expressions = listOf(prepared.expression),
                        moduleRoute = prepared.route,
                    ),
                    "Edit graph configuration",
                ),
            )
            selectedIds = setOf(existing.id)
            status = "Graph configuration updated; reopen it to continue direct manipulation"
            return true
        }
        val centerX = -document.viewport.panX / document.viewport.zoom + 72f
        val centerY = -document.viewport.panY / document.viewport.zoom + 96f
        val element = GraphConfigurationElement(
            id = "graph-${UUID.randomUUID()}",
            graphKind = prepared.kind,
            expressions = listOf(prepared.expression),
            sourceElementIds = emptyList(),
            moduleRoute = prepared.route,
            bounds = SmartBoardBounds(centerX, centerY, centerX + 360f, centerY + 220f),
            createdAt = now(),
        )
        execute(AddElementCommand(element))
        selectedIds = setOf(element.id)
        status = "Graph configuration inserted as an editable board object"
        return true
    }

    fun editPhysicsExpression(element: PhysicsExpressionElement, source: String) {
        val valid = SafeLatexPreview.validate(source).getOrElse {
            status = it.message ?: "Invalid notation"
            return
        }
        val analysis = PhysicsBoardAnalyzer().analyze(valid, element.recognitionConfidence)
        execute(
            ReplaceElementCommand(
                element,
                element.copy(
                    correctedSource = valid,
                    contentType = analysis.contentType,
                    topic = analysis.topic,
                    formulaId = analysis.equations.firstOrNull()?.formulaId,
                    ambiguities = analysis.ambiguities.map { it.message },
                    warnings = analysis.warnings,
                ),
                "Edit Physics expression",
            ),
        )
        status = "Physics expression and contextual actions updated"
    }

    fun runPhysicsAction(action: PhysicsActionType, handoff: (String, String) -> Unit = { _, _ -> }) {
        val expression = selectedPhysicsExpression ?: run {
            status = "Select one Physics expression first"
            return
        }
        viewModelScope.launch {
            runningAction = true
            runCatching { physicsIntelligence.execute(expression, action, now()) }
                .onSuccess { outcome ->
                    outcome.result?.let {
                        execute(AddElementCommand(it))
                        selectedIds = setOf(it.id)
                    }
                    if (outcome.handoffRoute != null) handoff(outcome.handoffRoute, outcome.handoffPayload.orEmpty())
                    status = outcome.message
                }
                .onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
            runningAction = false
        }
    }

    fun refreshIntelligence(command: String? = null, explicit: Boolean = command != null) {
        if (command != null && applySubjectCommand(command)) return
        val boardIntelligencePreferences = intelligenceMemory.get(document.id)?.userPreferences
        if (!explicit && (!preferences.intelligenceSuggestionsEnabled ||
                preferences.intelligenceMode == com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode.MANUAL ||
                boardIntelligencePreferences?.suggestionsDisabledForBoard == true ||
                (boardIntelligencePreferences?.suggestionSnoozedUntil ?: 0L) > now())
        ) return
        intelligenceJob?.cancel()
        intelligenceJob = viewModelScope.launch {
            intelligenceBusy = true
            runCatching {
                val context = intelligenceContextBuilder.build(document, selectedIds, intelligenceMemory.get(document.id)?.activeProblemId)
                val understood = intelligenceOrchestrator.understand(SmartBoardUnderstandingRequest(context, command, explicit))
                val recommendations = if (boardIntelligencePreferences?.suggestionsDisabledForBoard == true) {
                    emptyList()
                } else {
                    intelligenceOrchestrator.recommendActions(understood.context)
                }
                Triple(understood.context, understood, recommendations)
            }.onSuccess { (context, understood, recommendations) ->
                intelligenceContext = context
                intelligenceUnderstanding = understood
                intelligenceRecommendations = recommendations
                if (recommendations.isNotEmpty()) recordIntelligenceEvent(SmartBoardIntelligenceEventType.RECOMMENDATIONS_SHOWN)
                if (understood.clarification != null) recordIntelligenceEvent(SmartBoardIntelligenceEventType.CLARIFICATION_REQUESTED)
                if (!context.serviceAvailability.aiAvailable) recordIntelligenceEvent(SmartBoardIntelligenceEventType.OFFLINE_FALLBACK_USED)
                status = understood.clarification ?: if (recommendations.isEmpty()) "Analysis complete" else "${recommendations.count { it.disabledReason == null }} smart suggestion(s) available"
                val current = intelligenceMemory.get(document.id) ?: SmartBoardSessionMemory.empty(document.id, now())
                saveIntelligenceMemory(
                    current.copy(
                        activeProblemId = understood.problemState.id,
                        activeWorkflow = activeIntelligenceWorkflow,
                        lastUpdatedAt = now(),
                    ),
                )
            }.onFailure {
                if (it !is kotlinx.coroutines.CancellationException) status = SmartBoardSecurityPolicy.safeError(it)
            }
            intelligenceBusy = false
        }
    }

    private fun applySubjectCommand(command: String): Boolean {
        val normalized = command.trim().lowercase()
        val subject = when {
            "auto detect" in normalized -> SmartBoardSubject.AUTO
            "mathematics" in normalized || Regex("""\bmaths?\b""").containsMatchIn(normalized) -> SmartBoardSubject.MATHEMATICS
            "physics" in normalized -> SmartBoardSubject.PHYSICS
            "chemistry" in normalized -> SmartBoardSubject.CHEMISTRY
            "english" in normalized -> SmartBoardSubject.ENGLISH
            "biology" in normalized -> SmartBoardSubject.BIOLOGY
            else -> return false
        }
        return when {
            normalized.startsWith("set subject") || normalized.startsWith("use auto") -> {
                setBoardSubject(subject)
                true
            }
            normalized.contains("this") || normalized.contains("selected content") || normalized.startsWith("analyze") -> {
                if (subject == SmartBoardSubject.AUTO || selectedIds.isEmpty()) {
                    status = "Select content and choose a specific subject"
                } else {
                    assignSelectedSubject(subject)
                }
                true
            }
            else -> false
        }
    }

    fun dismissRecommendation(id: String) {
        val memory = intelligenceMemory.dismiss(document.id, id, now())
        saveIntelligenceMemory(memory)
        intelligenceRecommendations = intelligenceRecommendations.filterNot { it.id == id }
        recordIntelligenceEvent(SmartBoardIntelligenceEventType.RECOMMENDATION_DISMISSED)
        status = "Suggestion dismissed for this Board context"
    }

    fun snoozeIntelligenceSuggestions(durationMillis: Long = 30 * 60 * 1000L) {
        val current = intelligenceMemory.get(document.id) ?: SmartBoardSessionMemory.empty(document.id, now())
        saveIntelligenceMemory(
            current.copy(
                userPreferences = current.userPreferences.copy(
                    suggestionSnoozedUntil = now() + durationMillis.coerceAtLeast(60_000L),
                ),
                lastUpdatedAt = now(),
            ),
        )
        intelligenceRecommendations = emptyList()
        status = "Smart suggestions snoozed for this Board"
    }

    fun setBoardIntelligenceSuggestionsEnabled(enabled: Boolean) {
        val current = intelligenceMemory.get(document.id) ?: SmartBoardSessionMemory.empty(document.id, now())
        saveIntelligenceMemory(
            current.copy(
                userPreferences = current.userPreferences.copy(
                    suggestionsDisabledForBoard = !enabled,
                    suggestionSnoozedUntil = if (enabled) null else current.userPreferences.suggestionSnoozedUntil,
                ),
                lastUpdatedAt = now(),
            ),
        )
        if (enabled) refreshIntelligence(explicit = true) else intelligenceRecommendations = emptyList()
        status = if (enabled) "Smart suggestions enabled for this Board" else "Smart suggestions disabled for this Board"
    }

    fun boardIntelligenceSuggestionsEnabled(): Boolean =
        intelligenceMemory.get(document.id)?.userPreferences?.suggestionsDisabledForBoard != true

    fun resolveIntelligenceAmbiguity(id: String, value: String) {
        val memory = intelligenceMemory.resolveAmbiguity(document.id, id, value, now())
        saveIntelligenceMemory(memory)
        refreshIntelligence(explicit = true)
    }

    fun executeRecommendation(
        recommendation: SmartBoardRecommendation,
        handoff: (String, String) -> Unit = { _, _ -> },
    ) {
        val context = intelligenceContext ?: run {
            refreshIntelligence(explicit = true)
            status = "Refresh intelligence, then choose the suggestion again"
            return
        }
        val toolId = recommendation.toolId ?: return
        if (recommendation.disabledReason != null) {
            status = recommendation.disabledReason
            return
        }
        viewModelScope.launch {
            intelligenceBusy = true
            val source = context.elements.filter { it.id in recommendation.sourceElementIds }
                .joinToString("\n") { it.summary.removePrefix("[UNTRUSTED_BOARD_CONTENT] ") }
            val result = runCatching {
                intelligenceTools.execute(
                    SmartBoardToolCall(
                        "recommendation-${UUID.randomUUID()}", toolId, document.id, document.subject,
                        recommendation.sourceElementIds, mapOf("source" to source), explicitUserApproval = true,
                    ),
                    context,
                )
            }
            result.onSuccess {
                recordIntelligenceEvent(
                    SmartBoardIntelligenceEventType.RECOMMENDATION_ACCEPTED,
                    capability = toolId,
                    succeeded = it.success,
                )
                if (it.verificationStatus != com.indianservers.smartboard.smartboard.intelligence.SmartBoardVerificationStatus.INCONCLUSIVE) {
                    recordIntelligenceEvent(
                        SmartBoardIntelligenceEventType.VERIFICATION_COMPLETED,
                        capability = toolId,
                        succeeded = it.success,
                    )
                }
                if (it.moduleRoute != null) recordIntelligenceEvent(SmartBoardIntelligenceEventType.INTELLIGENT_VISUAL_OPENED, toolId, it.success)
                applyIntelligenceToolResult(it, recommendation.sourceElementIds, handoff)
                val action = SmartBoardActionHistoryEntry(
                    "history-${UUID.randomUUID()}", toolId, recommendation.sourceElementIds, it.success, now(),
                )
                saveIntelligenceMemory(intelligenceMemory.record(document.id, action, now()))
                refreshIntelligence(explicit = true)
            }.onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
            intelligenceBusy = false
        }
    }

    fun planIntelligenceWorkflow(command: String) {
        if (command.isBlank()) return
        viewModelScope.launch {
            intelligenceBusy = true
            runCatching {
                val context = intelligenceContextBuilder.build(document, selectedIds, intelligenceMemory.get(document.id)?.activeProblemId)
                val plan = intelligenceOrchestrator.planWorkflow(SmartBoardWorkflowRequest(context, command))
                context to plan
            }.onSuccess { (context, plan) ->
                intelligenceContext = context
                activeIntelligenceWorkflow = plan
                recordIntelligenceEvent(SmartBoardIntelligenceEventType.WORKFLOW_STARTED)
                val current = intelligenceMemory.get(document.id) ?: SmartBoardSessionMemory.empty(document.id, now())
                saveIntelligenceMemory(current.copy(activeWorkflow = plan, lastUpdatedAt = now()))
                status = "Workflow ready for review; no step has run"
            }.onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
            intelligenceBusy = false
        }
    }

    fun executeNextWorkflowStep(
        handoff: (String, String) -> Unit = { _, _ -> },
        approve: Boolean = true,
    ) {
        val plan = activeIntelligenceWorkflow ?: return
        val step = plan.steps.firstOrNull { it.status in setOf(WorkflowStepStatus.PENDING, WorkflowStepStatus.FAILED) } ?: run {
            status = "Workflow complete"
            return
        }
        val context = intelligenceContext ?: return
        viewModelScope.launch {
            intelligenceBusy = true
            runCatching {
                intelligenceOrchestrator.executeApprovedStep(SmartBoardWorkflowStepRequest(context, plan, step.id, approve))
            }.onSuccess { outcome ->
                activeIntelligenceWorkflow = outcome.plan
                outcome.toolResult?.let { applyIntelligenceToolResult(it, step.inputElementIds, handoff) }
                val current = intelligenceMemory.get(document.id) ?: SmartBoardSessionMemory.empty(document.id, now())
                saveIntelligenceMemory(current.copy(activeWorkflow = outcome.plan, lastUpdatedAt = now()))
                status = if (outcome.step.status == WorkflowStepStatus.COMPLETED) "Workflow step completed and verified" else "Workflow step failed"
                if (outcome.plan.steps.all { it.status == WorkflowStepStatus.COMPLETED }) {
                    recordIntelligenceEvent(SmartBoardIntelligenceEventType.WORKFLOW_COMPLETED, succeeded = true)
                }
            }.onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
            intelligenceBusy = false
        }
    }

    fun cancelIntelligenceWorkflow() {
        activeIntelligenceWorkflow ?: return
        activeIntelligenceWorkflow = null
        val current = intelligenceMemory.get(document.id) ?: SmartBoardSessionMemory.empty(document.id, now())
        saveIntelligenceMemory(current.copy(activeWorkflow = null, lastUpdatedAt = now()))
        recordIntelligenceEvent(SmartBoardIntelligenceEventType.WORKFLOW_CANCELLED)
        status = "Workflow cancelled; Board content unchanged"
    }

    private fun recordIntelligenceEvent(
        type: SmartBoardIntelligenceEventType,
        capability: String? = null,
        succeeded: Boolean? = null,
    ) {
        intelligenceAnalytics.record(
            SmartBoardIntelligenceEvent(
                type = type,
                subject = document.subject.name,
                mode = preferences.intelligenceMode.name,
                capability = capability?.take(80),
                succeeded = succeeded,
                occurredAt = now(),
            ),
        )
    }

    private fun recordSubjectEvent(
        type: SmartBoardMultiSubjectEventType,
        subject: SmartBoardSubject?,
        confidenceLevel: String? = null,
        cacheHit: Boolean? = null,
        latencyBucket: String? = null,
    ) {
        multiSubjectAnalytics.record(
            SmartBoardMultiSubjectEvent(type, subject, confidenceLevel, cacheHit, latencyBucket, now()),
        )
    }

    private fun applyIntelligenceToolResult(
        result: SmartBoardToolResult,
        sourceIds: List<String>,
        handoff: (String, String) -> Unit,
    ) {
        if (!result.success) {
            status = result.safeMessage ?: "The controlled tool could not complete"
            return
        }
        result.producedElement?.let {
            execute(AddElementCommand(it))
            selectedIds = setOf(it.id)
            execute(GroupCommand(SmartBoardRelationship(
                "derived-${UUID.randomUUID()}", SmartBoardRelationshipType.DERIVED_FROM,
                sourceIds + it.id, now(),
            )))
        }
        if (result.producedElement == null && result.moduleRoute == null && (result.exact != null || result.details.isNotEmpty())) {
            val source = document.elements.firstOrNull { it.id in sourceIds }
            val bounds = source?.bounds ?: SmartBoardBounds(20f, 20f, 360f, 130f)
            val top = bounds.bottom + 20f
            val element = ActionResultElement(
                "intelligence-result-${UUID.randomUUID()}", SmartBoardResultKind.CAS, result.title,
                result.exact, result.approximate, result.details.take(100), result.assumptions.take(50), sourceIds,
                result.verificationStatus in setOf(
                    com.indianservers.smartboard.smartboard.intelligence.SmartBoardVerificationStatus.VERIFIED,
                    com.indianservers.smartboard.smartboard.intelligence.SmartBoardVerificationStatus.VERIFIED_WITH_CONDITIONS,
                    com.indianservers.smartboard.smartboard.intelligence.SmartBoardVerificationStatus.NUMERICALLY_VERIFIED,
                ),
                SmartBoardBounds(bounds.left, top, maxOf(bounds.right, bounds.left + 340f), top + 120f), now(),
            )
            execute(AddElementCommand(element))
            selectedIds = setOf(element.id)
            execute(GroupCommand(SmartBoardRelationship(
                "derived-${UUID.randomUUID()}", SmartBoardRelationshipType.DERIVED_FROM,
                sourceIds + element.id, now(),
            )))
        }
        result.moduleRoute?.let { route -> handoff(route, result.modulePayload.orEmpty()) }
        status = result.title
    }

    private fun scheduleIntelligence() {
        if (!preferences.intelligenceSuggestionsEnabled ||
            preferences.intelligenceMode == com.indianservers.smartboard.smartboard.models.SmartBoardIntelligenceMode.MANUAL
        ) return
        intelligenceJob?.cancel()
        intelligenceJob = viewModelScope.launch {
            delay(900)
            refreshIntelligence()
        }
    }

    private fun saveIntelligenceMemory(memory: SmartBoardSessionMemory) {
        intelligenceMemory.put(memory)
        viewModelScope.launch { runCatching { repository.saveIntelligenceMemory(memory) } }
    }

    fun runMathAction(action: SmartBoardMathAction, handoff: (String, String) -> Unit = { _, _ -> }) {
        val expression = selectedExpression ?: run {
            status = "Select one mathematical expression first"
            return
        }
        val authorization = SmartBoardSecurityPolicy.authorizeUserAction(action, listOf(expression.displayLatex), explicitUserGesture = true)
            .getOrElse {
                status = SmartBoardSecurityPolicy.safeError(it)
                return
            }
        when (action) {
            SmartBoardMathAction.PLOT_2D, SmartBoardMathAction.PLOT_3D -> {
                val prepared = SmartBoardGraphAdapter.prepare(authorization.source, action == SmartBoardMathAction.PLOT_3D).getOrElse {
                    status = SmartBoardSecurityPolicy.safeError(it)
                    return
                }
                val element = GraphConfigurationElement(
                    id = "graph-${UUID.randomUUID()}",
                    graphKind = prepared.kind,
                    expressions = listOf(prepared.expression),
                    sourceElementIds = listOf(expression.id),
                    moduleRoute = prepared.route,
                    bounds = expression.bounds.translate(SmartBoardPoint(0f, expression.bounds.height + 24f)).expand(12f),
                    createdAt = now(),
                )
                execute(AddElementCommand(element))
                selectedIds = setOf(element.id)
                handoff(prepared.route, prepared.expression)
            }
            SmartBoardMathAction.OPEN_GEOMETRY_2D -> handoff("geometry2d", authorization.source)
            SmartBoardMathAction.OPEN_GEOMETRY_3D -> handoff("geometry3d", authorization.source)
            SmartBoardMathAction.STATISTICS -> viewModelScope.launch(Dispatchers.Default) {
                runningAction = true
                runCatching { SmartBoardStatisticsAdapter.summarize(authorization.source) }
                    .onSuccess { statistics ->
                        withContext(Dispatchers.Main) {
                            insertResult(expression, "Descriptive statistics", statistics.summary, verified = true)
                        }
                    }.onFailure { withContext(Dispatchers.Main) { status = SmartBoardSecurityPolicy.safeError(it) } }
                runningAction = false
            }
            SmartBoardMathAction.VERIFY_WORK -> status = "Select a solution sequence to verify"
            else -> viewModelScope.launch {
                runningAction = true
                runCatching { casAdapter.execute(authorization.source, action) }
                    .onSuccess { result ->
                        insertResult(
                            expression,
                            result.title,
                            result.steps,
                            result.verified,
                            result.exact,
                            result.approximate,
                            result.assumptions,
                        )
                        if (!result.supported) status = "Existing engine does not support this operation"
                    }
                    .onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
                runningAction = false
            }
        }
    }

    fun requestTutorHint(nextStepOnly: Boolean = false) {
        val expression = selectedExpression ?: run {
            status = "Select one mathematical expression first"
            return
        }
        val priorHints = document.elements.filterIsInstance<ActionResultElement>().count {
            it.kind == SmartBoardResultKind.TUTOR && expression.id in it.sourceElementIds && it.title.startsWith("Hint")
        }
        val level = (priorHints + 1).coerceIn(1, 7)
        viewModelScope.launch(Dispatchers.Default) {
            val response = runCatching {
                tutor.respond(
                    SmartBoardTutorRequest(
                        problem = expression.displayLatex,
                        mode = if (nextStepOnly) SmartBoardTutorMode.NEXT_STEP else SmartBoardTutorMode.HINT,
                        hintLevel = level,
                    ),
                )
            }
            withContext(Dispatchers.Main) {
                response.onSuccess { result ->
                    val top = expression.bounds.bottom + 20f
                    val card = ActionResultElement(
                        id = "tutor-${UUID.randomUUID()}",
                        kind = SmartBoardResultKind.TUTOR,
                        title = result.title,
                        exact = null,
                        approximate = null,
                        details = result.content,
                        assumptions = result.warnings,
                        sourceElementIds = listOf(expression.id),
                        verified = result.verified,
                        bounds = SmartBoardBounds(expression.bounds.left, top, maxOf(expression.bounds.right, expression.bounds.left + 320f), top + 100f),
                        createdAt = now(),
                    )
                    execute(AddElementCommand(card))
                    selectedIds = setOf(card.id)
                    status = if (result.degraded) "Tutor responded in degraded local mode" else "Tutor guidance inserted"
                }.onFailure { status = SmartBoardSecurityPolicy.safeError(it) }
            }
        }
    }

    fun refreshTutorContext() {
        val context = tutorContextBuilder.build(
            document = document,
            selection = selectedIds,
            messages = tutorConversation.messages,
            activeProblemId = tutorConversation.activeProblemId,
            recentActions = intelligenceMemory.get(document.id)?.recentActions.orEmpty(),
        )
        tutorContext = context
        tutorConversation = tutorConversation.copy(activeSubject = context.primarySubject, updatedAt = now())
        tutorJob?.cancel()
        tutorJob = viewModelScope.launch(Dispatchers.Default) {
            val prompts = runCatching { unifiedTutor.suggestPrompts(context) }.getOrDefault(emptyList())
            withContext(Dispatchers.Main) { tutorSuggestedPrompts = prompts }
        }
    }

    fun setUnifiedTutorMode(mode: UnifiedTutorMode) {
        tutorConversation = tutorConversation.copy(activeMode = mode, updatedAt = now())
        saveTutorConversation()
        status = "${mode.name.lowercase().replace('_', ' ')} tutor mode selected"
        recordTutorEvent(com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.MODE_SELECTED, mode)
    }

    fun sendTutorMessage(message: String = "") {
        if (selectedIds.isEmpty()) {
            status = "Select the work you want the tutor to inspect"
            return
        }
        refreshTutorContext()
        val context = tutorContext ?: return
        val mode = tutorConversation.activeMode
        val problemId = context.activeProblemId ?: context.contextFingerprint
        val previousLevel = tutorConversation.shownHintLevels[problemId] ?: 0
        val hintLevel = if (mode == UnifiedTutorMode.HINT) (previousLevel + 1).coerceIn(1, 7) else 1
        val safeMessage = message.trim().take(2_000).ifBlank { mode.name.lowercase().replace('_', ' ') }
        val userMessage = SmartBoardTutorMessage(
            "tutor-user-${UUID.randomUUID()}", "user", safeMessage,
            context.primarySubject ?: SmartBoardSubject.GENERAL, null, context.selectedElementIds, now(),
        )
        tutorConversation = tutorConversation.copy(
            messages = (tutorConversation.messages + userMessage).takeLast(100),
            shownHintLevels = if (mode == UnifiedTutorMode.HINT) {
                tutorConversation.shownHintLevels + (problemId to hintLevel)
            } else tutorConversation.shownHintLevels,
            activeProblemId = problemId,
            updatedAt = now(),
        )
        tutorBusy = true
        status = "Smart Board Tutor is checking selected content"
        recordTutorEvent(
            when (mode) {
                UnifiedTutorMode.HINT -> com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.HINT_REQUESTED
                UnifiedTutorMode.NEXT_STEP -> com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.NEXT_STEP_REQUESTED
                UnifiedTutorMode.CHECK_MY_WORK, UnifiedTutorMode.FIND_MY_MISTAKE ->
                    com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.VERIFICATION_RUN
                else -> com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.SUBJECT_CONTEXT_SELECTED
            },
            mode,
        )
        tutorJob?.cancel()
        tutorJob = viewModelScope.launch(Dispatchers.Default) {
            val outcome = runCatching {
                val response = unifiedTutor.respond(UnifiedTutorRequest(context, mode, safeMessage, hintLevel))
                response.copy(verificationStatus = unifiedTutor.verifyResponse(response))
            }
            withContext(Dispatchers.Main) {
                outcome.onSuccess { response ->
                    tutorLastResponse = response
                    val tutorMessage = SmartBoardTutorMessage(
                        response.id, "tutor", response.message, response.subject, response.verificationStatus,
                        response.referencedElementIds, response.createdAt,
                    )
                    tutorConversation = tutorConversation.copy(
                        activeSubject = response.subject,
                        messages = (tutorConversation.messages + tutorMessage).takeLast(100),
                        updatedAt = now(),
                    )
                    status = "${response.subject.name.lowercase().replaceFirstChar(Char::titlecase)} tutor: ${response.verificationStatus.name.lowercase().replace('_', ' ')}"
                    if (response.verification?.firstInvalidStepId != null) {
                        recordTutorEvent(com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.FIRST_ERROR_DETECTED, mode)
                    }
                    saveTutorConversation()
                }.onFailure {
                    status = SmartBoardSecurityPolicy.safeError(it)
                }
                tutorBusy = false
            }
        }
    }

    fun stopTutor() {
        tutorJob?.cancel()
        tutorBusy = false
        status = "Tutor request stopped; Board content was not changed"
    }

    fun clearTutorConversation() {
        tutorJob?.cancel()
        tutorConversation = SmartBoardTutorConversation.empty(document.id, now())
        tutorLastResponse = null
        tutorSuggestedPrompts = emptyList()
        refreshTutorContext()
        saveTutorConversation()
        status = "Tutor conversation cleared; Board content remains"
    }

    fun insertLastTutorResponse() {
        val response = tutorLastResponse ?: run {
            status = "No tutor response is ready to insert"
            return
        }
        val sourceElements = document.elements.filter { it.id in response.referencedElementIds }
        val anchor = sourceElements.maxByOrNull { it.bounds.bottom } ?: run {
            status = "Referenced source content is no longer available"
            return
        }
        val top = anchor.bounds.bottom + 20f
        val verified = response.verificationStatus in setOf(
            com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorVerificationStatus.VERIFIED,
            com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorVerificationStatus.NUMERICALLY_VERIFIED,
            com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorVerificationStatus.RULE_VERIFIED,
            com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorVerificationStatus.MODEL_REFERENCE_VERIFIED,
        )
        val card = ActionResultElement(
            id = "tutor-output-${UUID.randomUUID()}",
            kind = SmartBoardResultKind.TUTOR,
            title = "${response.subject.name.lowercase().replaceFirstChar(Char::titlecase)} Tutor · ${response.mode.name.lowercase().replace('_', ' ')}",
            exact = null,
            approximate = null,
            details = response.structuredContent.map { it.content }.ifEmpty { listOf(response.message) }.take(100),
            assumptions = (response.warnings + "Verification: ${response.verificationStatus.name.lowercase().replace('_', ' ')}").take(50),
            sourceElementIds = response.referencedElementIds,
            verified = verified,
            bounds = SmartBoardBounds(anchor.bounds.left, top, maxOf(anchor.bounds.right, anchor.bounds.left + 360f), top + 130f),
            createdAt = now(),
        )
        val relationship = SmartBoardRelationship(
            "tutor-link-${UUID.randomUUID()}", SmartBoardRelationshipType.EXPLAINS,
            response.referencedElementIds + card.id, now(),
        )
        execute(InsertTutorOutputCommand(card, relationship))
        selectedIds = setOf(card.id)
        status = "Tutor output inserted with source links; undo is available"
        recordTutorEvent(com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType.OUTPUT_INSERTED, response.mode)
    }

    private fun saveTutorConversation() {
        val snapshot = tutorConversation
        viewModelScope.launch { runCatching { repository.saveTutorConversation(snapshot) } }
    }

    private fun recordTutorEvent(
        type: com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEventType,
        mode: UnifiedTutorMode? = tutorConversation.activeMode,
    ) {
        val context = tutorContext
        tutorAnalytics.record(
            com.indianservers.smartboard.smartboard.tutor.SmartBoardTutorEvent(
                type = type,
                subject = context?.primarySubject,
                supportingSubjectCount = context?.supportingSubjects?.size ?: 0,
                mode = mode,
                verificationStatus = tutorLastResponse?.verificationStatus,
                selectedElementCount = selectedIds.size.coerceAtMost(32),
                occurredAt = now(),
            ),
        )
    }

    private fun insertResult(
        source: MathExpressionElement,
        title: String,
        details: List<String>,
        verified: Boolean,
        exact: String? = null,
        approximate: String? = null,
        assumptions: List<String> = emptyList(),
    ) {
        val top = source.bounds.bottom + 20f
        val result = ActionResultElement(
            id = "result-${UUID.randomUUID()}",
            kind = if (title.contains("statistic", true)) SmartBoardResultKind.STATISTICS else SmartBoardResultKind.CAS,
            title = title,
            exact = exact,
            approximate = approximate,
            details = details.take(100),
            assumptions = assumptions,
            sourceElementIds = listOf(source.id),
            verified = verified,
            bounds = SmartBoardBounds(source.bounds.left, top, maxOf(source.bounds.right, source.bounds.left + 320f), top + 110f),
            createdAt = now(),
        )
        execute(AddElementCommand(result))
        selectedIds = setOf(result.id)
        status = if (verified) "Verified result inserted" else "Unverified result inserted"
    }

    fun setSourceHandwritingVisibility(element: MathExpressionElement, hidden: Boolean) {
        val sources = document.elements.filterIsInstance<StrokeElement>().filter { it.id in element.sourceStrokeIds }
        if (sources.isEmpty()) return
        execute(SetStrokeVisibilityCommand(sources.associate { it.id to it.hidden }, hidden))
        status = if (hidden) "Source handwriting hidden; expression remains linked" else "Source handwriting shown"
    }

    private fun insertionBounds(width: Float, height: Float): SmartBoardBounds {
        val left = -document.viewport.panX / document.viewport.zoom + 72f
        val top = -document.viewport.panY / document.viewport.zoom + 96f
        return SmartBoardBounds(left, top, left + width, top + height)
    }

    private fun nearbyShapes(bounds: SmartBoardBounds): List<ShapeElement> =
        document.elements.filterIsInstance<ShapeElement>()
            .filterNot(ShapeElement::hidden)
            .filter { it.bounds.intersects(bounds.expand(maxOf(80f, bounds.width))) }
            .take(32)

    private fun contextualize(
        snapshot: StreamingRecognitionSnapshot,
        bounds: SmartBoardBounds,
    ): ContextualRerankOutcome = SmartBoardContextualRecognitionReranker.rerank(
        snapshot,
        RecognitionContext.from(document, bounds, preferences.recognitionQualityTier),
        if (preferences.recognitionPersonalizationEnabled) recognitionPersonalizationProfile
        else RecognitionPersonalizationProfile.Empty,
    )

    private fun recordRecognitionDiagnostic(
        snapshot: StreamingRecognitionSnapshot,
        input: RecognitionDiagnosticInput,
    ) {
        if (!preferences.recognitionDiagnosticsEnabled) return
        recognitionDiagnostics.record(
            RecognitionDiagnosticEvent(
                input = input,
                latency = when (snapshot.latencyMillis) {
                    in 0..149 -> RecognitionLatencyBucket.UNDER_150_MS
                    in 150..500 -> RecognitionLatencyBucket.FROM_150_TO_500_MS
                    else -> RecognitionLatencyBucket.OVER_500_MS
                },
                confidence = confidenceBucket(snapshot.result.confidence),
                candidateCount = snapshot.candidates.size.coerceAtMost(16),
                selectedRank = null,
                corrected = null,
                occurredAt = now(),
            ),
        )
    }

    private fun confidenceBucket(value: Float?): RecognitionConfidenceBucket = when {
        value == null || value < .60f -> RecognitionConfidenceBucket.LOW
        value < .85f -> RecognitionConfidenceBucket.MEDIUM
        else -> RecognitionConfidenceBucket.HIGH
    }

    private fun execute(command: SmartBoardCommand) {
        document = history.execute(document, command, now())
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        savedStateHandle["smartBoardDocumentId"] = document.id
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(650)
            runCatching {
                repository.saveRecovery(document)
                repository.save(document)
                recentBoards = repository.recent()
                imageAssets.cleanupUnreferenced(repository.referencedAssetIds())
            }.onFailure { status = "Autosave unavailable: ${it.message ?: "storage error"}" }
        }
    }

    private fun now() = System.currentTimeMillis()
}

private fun SmartBoardSubject.displayName() = if (this == SmartBoardSubject.AUTO) "Auto Detect" else name.lowercase().replaceFirstChar(Char::titlecase)
