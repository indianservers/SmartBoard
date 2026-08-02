package com.indianservers.smartboard.smartboard

import com.indianservers.smartboard.smartboard.models.DimensionalStatus
import com.indianservers.smartboard.smartboard.models.PhysicsActionType
import com.indianservers.smartboard.smartboard.models.PhysicsContentType
import com.indianservers.smartboard.smartboard.models.PhysicsEngineMetadata
import com.indianservers.smartboard.smartboard.models.PhysicsExpressionElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultElement
import com.indianservers.smartboard.smartboard.models.PhysicsResultStatus
import com.indianservers.smartboard.smartboard.models.SmartBoardBounds
import com.indianservers.smartboard.smartboard.models.SmartBoardDocument
import com.indianservers.smartboard.smartboard.models.SmartBoardSubject
import com.indianservers.smartboard.smartboard.persistence.SmartBoardDocumentCodec
import com.indianservers.smartboard.smartboard.physics.ExistingPhysicsUnitAdapter
import com.indianservers.smartboard.smartboard.physics.PhysicsBoardAnalyzer
import com.indianservers.smartboard.smartboard.physics.PhysicsDiagramClassifier
import com.indianservers.smartboard.smartboard.physics.PhysicsDimensionalAnalyzer
import com.indianservers.smartboard.smartboard.physics.PhysicsFormulaMatcher
import com.indianservers.smartboard.smartboard.physics.PhysicsFormulaRearranger
import com.indianservers.smartboard.smartboard.physics.PhysicsActionResolver
import com.indianservers.smartboard.smartboard.physics.PhysicsMisconceptionDetector
import com.indianservers.smartboard.smartboard.physics.PhysicsNumericalSolver
import com.indianservers.smartboard.smartboard.physics.PhysicsQuantityParser
import com.indianservers.smartboard.smartboard.physics.PhysicsSignificantFigures
import com.indianservers.smartboard.smartboard.physics.PhysicsTutorEngine
import com.indianservers.smartboard.smartboard.physics.PhysicsUncertaintyAdapter
import com.indianservers.smartboard.smartboard.physics.PhysicsUnitConverter
import com.indianservers.smartboard.smartboard.physics.PhysicsVectorAdapter
import com.indianservers.smartboard.smartboard.physics.PhysicsWorkVerifier
import com.indianservers.smartboard.smartboard.recognition.MathematicsSubjectHandler
import com.indianservers.smartboard.smartboard.recognition.SmartBoardSubjectRegistry
import com.indianservers.smartboard.smartboard.physics.PhysicsSmartBoardSubjectHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartBoardPhysicsTest {
    @Test
    fun physicsBoardCreationIsExplicitAndMathematicsDefaultIsUnchanged() {
        assertEquals(SmartBoardSubject.MATHEMATICS, SmartBoardDocument.new("math", 1L).subject)
        assertEquals(SmartBoardSubject.PHYSICS, SmartBoardDocument.new("physics", 1L, subject = SmartBoardSubject.PHYSICS).subject)
    }

    @Test
    fun registryRoutesEachInstalledSubjectToItsOwnHandler() {
        val math = MathematicsSubjectHandler()
        val physics = PhysicsSmartBoardSubjectHandler()
        val registry = SmartBoardSubjectRegistry(listOf(math, physics))
        assertTrue(registry.handler(SmartBoardSubject.MATHEMATICS) === math)
        assertTrue(registry.handler(SmartBoardSubject.PHYSICS) === physics)
    }

    @Test
    fun analyzerClassifiesFormulaNumericalVectorAndDiagramContent() {
        val analyzer = PhysicsBoardAnalyzer()
        assertEquals(PhysicsContentType.FORMULA, analyzer.analyze("v = u + at").contentType)
        assertEquals(PhysicsContentType.NUMERICAL_PROBLEM, analyzer.analyze("u = 0 m/s\na = 2 m/s^2\nt = 5 s\nv = ?").contentType)
        assertEquals(PhysicsContentType.VECTOR, analyzer.analyze("(3, 4) m/s").contentType)
        assertNotNull(PhysicsDiagramClassifier.classify("battery resistor ammeter circuit"))
    }

    @Test
    fun formulaAndUnitsDelegateToExistingRegistries() {
        assertEquals("physics-final-velocity", PhysicsFormulaMatcher().match("v = u + at")?.id)
        val units = ExistingPhysicsUnitAdapter()
        val metre = requireNotNull(units.parseUnit("m").unit)
        val kilometre = requireNotNull(units.parseUnit("km").unit)
        assertTrue(units.areCompatible(metre, kilometre))
        assertEquals(1000.0, units.convert(1.0, kilometre, metre).value, 1e-9)
        val converted = PhysicsUnitConverter().convert("72 km/h to m/s")
        assertTrue(converted.verified)
        assertEquals(20.0, converted.outputValue ?: Double.NaN, 1e-9)
    }

    @Test
    fun quantityParserAndContextActionsRetainUnitsAndUnknowns() {
        val quantities = PhysicsQuantityParser().parse("u = 0 m/s\na = 2 m/s^2\nv = ?")
        assertEquals(3, quantities.size)
        assertEquals("v", quantities.single { it.scalarValue == null }.symbol)
        assertTrue(PhysicsActionType.SOLVE_NUMERICAL in PhysicsActionResolver().resolve(PhysicsContentType.NUMERICAL_PROBLEM, false))
    }

    @Test
    fun formulaRearrangementUsesExistingSymbolicCasAndRefusesAmbiguity() {
        assertTrue(PhysicsFormulaRearranger().rearrange("v = u + at", "v").verified)
        assertFalse(PhysicsFormulaRearranger().rearrange("v = u + at", "").verified)
    }

    @Test
    fun dimensionalAnalysisFindsConsistentAndInconsistentEquations() {
        val analyzer = PhysicsDimensionalAnalyzer()
        assertEquals(DimensionalStatus.CONSISTENT, analyzer.check("v = u + at").status)
        assertEquals(DimensionalStatus.INCONSISTENT, analyzer.check("s = u + at").status)
    }

    @Test
    fun numericalSolverUsesFormulaUnitsAndExistingEquationSolver() = runBlocking {
        val result = PhysicsNumericalSolver().solve("u = 0 m/s\na = 2 m/s^2\nt = 5 s\nv = ?")
        assertEquals(PhysicsResultStatus.VERIFIED, result.status)
        assertEquals(10.0, result.numericalResult ?: Double.NaN, 1e-8)
        assertTrue(result.engineMetadata.engines.any { it.contains("MathProblemSolver") })
    }

    @Test
    fun precisionUncertaintyAndVectorsAreDeterministic() {
        assertEquals(3, PhysicsSignificantFigures.count("0.0120"))
        assertEquals(12.3, PhysicsSignificantFigures.round(12.345, 3), 1e-10)
        val uncertainty = PhysicsUncertaintyAdapter.summarize(listOf(9.8, 10.0, 10.2))
        assertEquals(10.0, uncertainty.mean, 1e-10)
        val vector = PhysicsVectorAdapter.analyze("(3, 4) m/s")
        assertEquals(5.0, vector.magnitude, 1e-10)
    }

    @Test
    fun workVerifierIdentifiesFirstDimensionallyInvalidLine() {
        val verified = PhysicsWorkVerifier().verify("v = u + at\ns = u + at")
        assertEquals(2, verified.steps.size)
        assertEquals(1, verified.firstInvalidIndex)
    }

    @Test
    fun tutorAndMisconceptionsStayEvidenceBased() {
        val hint = PhysicsTutorEngine().hint("u = 0 m/s\na = 2 m/s^2\nt = 5 s\nv = ?", nextStepOnly = true)
        assertEquals(1, hint.guidance.size)
        assertTrue(PhysicsMisconceptionDetector.detect("Current is consumed by the resistor.").isNotEmpty())
        assertTrue(PhysicsMisconceptionDetector.detect("A correct unrelated sentence.").isEmpty())
    }

    @Test
    fun schemaTwoMathematicsBoardMigratesWithoutPhysicsElements() {
        val current = SmartBoardDocumentCodec.encode(SmartBoardDocument.new("legacy", 1L))
        val legacy = current.replaceFirst("SB|${SmartBoardDocument.CurrentSchemaVersion}|", "SB|2|")
        val decoded = SmartBoardDocumentCodec.decode(legacy)
        assertTrue(decoded.recovered)
        assertEquals(SmartBoardSubject.MATHEMATICS, decoded.document?.subject)
        assertTrue(decoded.document?.elements?.filterIsInstance<PhysicsExpressionElement>().isNullOrEmpty())
    }

    @Test
    fun schemaThreeRoundTripsPhysicsElementsWithoutChangingSubject() {
        val expression = PhysicsExpressionElement(
            "p1", "v = u + at", null, PhysicsContentType.FORMULA, null, "physics-final-velocity",
            emptyList(), .95f, emptyList(), emptyList(), SmartBoardBounds(0f, 0f, 200f, 50f), 2L,
        )
        val result = PhysicsResultElement(
            "r1", listOf("p1"), PhysicsActionType.CHECK_DIMENSIONS, "Dimension check", "v=u+at", null,
            emptyList(), "consistent", null, null, null, emptyList(), emptyList(), emptyList(),
            PhysicsEngineMetadata(listOf("existing"), true), PhysicsResultStatus.VERIFIED,
            SmartBoardBounds(0f, 60f, 300f, 150f), 3L,
        )
        val document = SmartBoardDocument.new("physics", 1L, subject = SmartBoardSubject.PHYSICS)
            .copy(updatedAt = 3L, elements = listOf(expression, result))
        val decoded = SmartBoardDocumentCodec.decode(SmartBoardDocumentCodec.encode(document))
        assertFalse(decoded.recovered)
        assertEquals(document, decoded.document)
    }
}
