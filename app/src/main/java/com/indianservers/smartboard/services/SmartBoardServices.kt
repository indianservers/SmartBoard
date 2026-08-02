package com.indianservers.smartboard.services

import com.indianservers.smartboard.biology.data.BundledBiologyCatalogue
import com.indianservers.smartboard.chemistry.data.BundledElementData
import com.indianservers.smartboard.core.AdvancedStatisticsEngine
import com.indianservers.smartboard.core.DescriptiveStatistics
import com.indianservers.smartboard.core.SymbolicCasEngine
import com.indianservers.smartboard.core.TypedGraphEngine
import com.indianservers.smartboard.core.TypedGraphExpressionParser
import com.indianservers.smartboard.core.Vec2
import com.indianservers.smartboard.input.CasHandwritingRecognizer
import com.indianservers.smartboard.input.CasPhotoMathRecognizer
import com.indianservers.smartboard.input.LocalRecognitionResult
import com.indianservers.smartboard.input.MathInkPoint
import com.indianservers.smartboard.physics.formulas.data.BundledPhysicsFormulaData

interface SmartBoardCasService {
    fun calculate(source: String, operation: String = "simplify"): String
}

interface SmartBoardGraphService {
    fun sample(source: String): Result<List<List<Vec2>>>
}

interface SmartBoardStatisticsService {
    fun summarize(values: List<Double>): DescriptiveStatistics
}

interface SmartBoardHandwritingService : AutoCloseable {
    fun recognize(
        strokes: List<List<MathInkPoint>>,
        width: Float,
        height: Float,
        context: String,
        onSuccess: (LocalRecognitionResult) -> Unit,
        onFailure: (String) -> Unit,
    )
}

interface SmartBoardPhotoRecognitionService {
    fun recognize(
        bytes: ByteArray,
        onSuccess: (LocalRecognitionResult) -> Unit,
        onFailure: (String) -> Unit,
    )
}

interface SmartBoardSubjectCatalogue {
    val biologyTopicCount: Int
    val chemistryElementCount: Int
}

interface SmartBoardPhysicsFormulaService {
    val formulaCount: Int
}

interface SmartBoardExternalNavigation {
    fun open(route: String, payload: String = "")
}

class StandaloneSmartBoardCasService(
    private val engine: SymbolicCasEngine = SymbolicCasEngine(),
) : SmartBoardCasService {
    override fun calculate(source: String, operation: String): String =
        engine.casRow(source, operation).exact
}

class StandaloneSmartBoardGraphService(
    private val engine: TypedGraphEngine = TypedGraphEngine(),
) : SmartBoardGraphService {
    override fun sample(source: String): Result<List<List<Vec2>>> = runCatching {
        val sample = engine.sample(TypedGraphExpressionParser.parse(source))
        buildList {
            addAll(sample.curves.map { segment -> segment.points })
            addAll(sample.implicitSegments.map { segment -> listOf(segment.start, segment.end) })
        }
    }
}

object StandaloneSmartBoardStatisticsService : SmartBoardStatisticsService {
    override fun summarize(values: List<Double>) = AdvancedStatisticsEngine.summarize(values)
}

class StandaloneSmartBoardHandwritingService(
    private val recognizer: CasHandwritingRecognizer = CasHandwritingRecognizer(),
) : SmartBoardHandwritingService {
    override fun recognize(
        strokes: List<List<MathInkPoint>>,
        width: Float,
        height: Float,
        context: String,
        onSuccess: (LocalRecognitionResult) -> Unit,
        onFailure: (String) -> Unit,
    ) = recognizer.recognize(strokes, width, height, context, onSuccess, onFailure)

    override fun close() = recognizer.close()
}

object StandaloneSmartBoardPhotoRecognitionService : SmartBoardPhotoRecognitionService {
    override fun recognize(
        bytes: ByteArray,
        onSuccess: (LocalRecognitionResult) -> Unit,
        onFailure: (String) -> Unit,
    ) = CasPhotoMathRecognizer.recognize(bytes, onSuccess, onFailure)
}

object StandaloneSmartBoardSubjectCatalogue : SmartBoardSubjectCatalogue {
    override val biologyTopicCount: Int
        get() = BundledBiologyCatalogue.catalogue.topics.size
    override val chemistryElementCount: Int
        get() = BundledElementData.elements.size
}

object StandaloneSmartBoardPhysicsFormulaService : SmartBoardPhysicsFormulaService {
    override val formulaCount: Int
        get() = BundledPhysicsFormulaData.catalogue.formulas.size
}
