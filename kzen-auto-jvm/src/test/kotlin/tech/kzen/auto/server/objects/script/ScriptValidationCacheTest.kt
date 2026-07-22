package tech.kzen.auto.server.objects.script

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.ScriptValidation
import tech.kzen.auto.common.objects.document.logic.StepValidation
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.platform.collect.persistentListOf
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/**
 * The [ScriptValidationCache] key must re-run validation exactly when notation it depends on changed:
 * the script document itself, a weakly-linked callee (a RunStep's return type reads the callee's `results`
 * signature), or an object-registry document (the global type-visibility scan) — and must NOT re-run on
 * an edit to an unrelated document. Cache behaviour is observed through the compute-invocation count
 * (the fixpoint itself is covered end-to-end by ScriptNotationTest via ScriptLogicCompiler).
 */
class ScriptValidationCacheTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val runParent = DocumentPath.parse("test/script-engine-run-test.yaml")
    private val runChild = DocumentPath.parse("test/script-engine-child-test.yaml")
    private val ifDocument = DocumentPath.parse("test/script-engine-if-test.yaml")
    private val registryDocument = DocumentPath.parse("auto-jvm/registry/registry-jvm.yaml")


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun identicalRequestsComputeOnceAndReturnEqualValidation() {
        val cache = ScriptValidationCache()
        val graphDefinition = graphDefinition(AutoTestUtils.readNotation())

        var computeCount = 0
        val computed = ScriptValidation(mapOf(
            ObjectPath.parse("main.steps/Seed") to StepValidation(null, null)))
        val compute = {
            computeCount++
            computed
        }

        val first = cache.scriptValidation(runParent, graphDefinition, compute)
        val second = cache.scriptValidation(runParent, graphDefinition, compute)

        assertEquals(1, computeCount, "second identical request must be a cache hit")
        assertEquals(computed, first)
        assertEquals(first, second)
    }


    @Test
    fun ownDocumentEditRecomputes() {
        assertEquals(2, computeCountAcrossEdit {
            edit(it, ObjectLocation(runParent, ObjectPath.parse("main.steps/Seed")),
                "code", ScalarAttributeNotation("7"))
        })
    }


    @Test
    fun unrelatedDocumentEditStaysCached() {
        assertEquals(1, computeCountAcrossEdit {
            edit(it, ObjectLocation(ifDocument, ObjectPath.parse("main.steps/Flag")),
                "code", ScalarAttributeNotation("false"))
        })
    }


    @Test
    fun linkedCalleeEditRecomputes() {
        assertEquals(2, computeCountAcrossEdit {
            edit(it, ObjectLocation(runChild, ObjectPath.parse("main.steps/Plus")),
                "code", ScalarAttributeNotation("number + 100"))
        })
    }


    @Test
    fun registryDocumentEditRecomputes() {
        assertEquals(2, computeCountAcrossEdit {
            edit(it, ObjectLocation(registryDocument, ObjectPath.parse("main")),
                "classes", ListAttributeNotation(persistentListOf(
                    ScalarAttributeNotation("kotlin.ranges.IntRange"),
                    ScalarAttributeNotation("kotlin.ranges.CharRange"))))
        })
    }


    //-----------------------------------------------------------------------------------------------------------------
    // End-to-end over the editor's path: ScriptValidator is graph-instantiated with the cache arriving via
    // @Service injection (the graphEnvironment registration this exercises fails only at runtime), validates,
    // and a repeat request round-trips the identical (cached) result. Instantiates only the validator's own
    // closure — the full ModelDetachedExecutor graph is not satisfiable in the test environment.
    @Test
    fun detachedValidationPathInjectsCacheAndRepeatsIdentically() {
        val context = KzenAutoContext.forTest()
        try {
            val attempt = runBlocking { context.graphStore.graphDefinition() }
            val validatorGraph = GraphCreator.createGraph(
                attempt.transitiveSuccessful.filterTransitive(ScriptConventions.scriptValidatorLocation),
                context.graphEnvironment)
            val validator = assertIs<DetachedAction>(
                validatorGraph.objectInstances[ScriptConventions.scriptValidatorLocation]?.reference)

            val request = ExecutionRequest(
                RequestParams.of(CommonRestApi.paramHostDocumentPath to runParent.asString()),
                null)

            val first = runBlocking { validator.execute(request) }
            val firstValidation = ScriptValidation.ofExecutionValue(
                assertIs<MapExecutionValue>(assertIs<ExecutionSuccess>(first).value))
            assertTrue(ObjectPath.parse("main.steps/Call") in firstValidation.stepValidations)

            val second = runBlocking { validator.execute(request) }
            assertEquals(first, second)
        }
        finally {
            context.close()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Computes once against the base notation, applies [editNotation], requests again — the returned count is
     * 1 if the edit left the cache key unchanged (hit), 2 if it invalidated (recompute).
     */
    private fun computeCountAcrossEdit(editNotation: (GraphNotation) -> GraphNotation): Int {
        val cache = ScriptValidationCache()
        val baseNotation = AutoTestUtils.readNotation()

        var computeCount = 0
        val compute = {
            computeCount++
            ScriptValidation(mapOf())
        }

        cache.scriptValidation(runParent, graphDefinition(baseNotation), compute)
        cache.scriptValidation(runParent, graphDefinition(editNotation(baseNotation)), compute)
        return computeCount
    }


    private fun graphDefinition(graphNotation: GraphNotation): GraphDefinition {
        return AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
    }


    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        attribute: String,
        value: AttributeNotation
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(location, AttributeName(attribute), value))
            .graphNotation
    }
}
