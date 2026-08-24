package tech.kzen.auto.server.objects.job.worker.definition

import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.objects.job.NominalReferenceCreator
import tech.kzen.auto.server.service.exec.GraphInstanceCache
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.context.GraphCreator
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame


/** Coverage for snapshot-scoped nominal resolution and the digest handed to migration compatibility checks. */
class WorkerDefinitionContextTest {
    private val hostDocument = DocumentPath.parse("test/job/reference/nominal-reference-test.yaml")
    private val crossTarget = ObjectLocation.parse(
        "test/job/reference/nominal-reference-target-test.yaml#CrossTarget")

    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun sameDocumentResolutionReusesTheRunGraphInstance() {
        val fixture = fixture()
        val target = location("SameTarget")

        val resolved = assertIs<WorkerDefinitionResolution.Resolved>(
            fixture.resolver.resolve(ObjectReference.parse("SameTarget"), location("SameHolder")))

        assertEquals(target, resolved.location)
        assertSame(fixture.runInstance[target]?.reference, resolved.value)
        assertEquals(
            GraphInstanceCache(GraphCreator, context.graphEnvironment)
                .cacheKey(fixture.definition, target),
            resolved.cacheKey)
    }


    @Test
    fun crossDocumentResolutionCachesOneInstanceAndOneCompatibilityKey() {
        val fixture = fixture()
        val reference = ObjectReference.parse(
            "test/job/reference/nominal-reference-target-test.yaml#CrossTarget")

        val first = assertIs<WorkerDefinitionResolution.Resolved>(
            fixture.resolver.resolve(reference, location("CrossHolder")))
        val second = assertIs<WorkerDefinitionResolution.Resolved>(
            fixture.resolver.resolve(reference, location("CrossHolder")))

        assertEquals(crossTarget, first.location)
        assertSame(first.value, second.value)
        assertEquals(first.cacheKey, second.cacheKey)
        assertEquals("cross", assertIs<ReferenceTarget>(first.value).value)
    }


    @Test
    fun runContextKeepsCrossDocumentOptOutTargetRunLocal() {
        val fixture = fixture()
        val reference = ObjectReference.parse(
            "test/job/reference/nominal-reference-target-test.yaml#OptOutTarget")

        val first = assertIs<WorkerDefinitionResolution.Resolved>(
            fixture.resolver.resolve(reference, location("CrossHolder")))
        val second = assertIs<WorkerDefinitionResolution.Resolved>(
            fixture.resolver.resolve(reference, location("CrossHolder")))

        assertSame(first.value, second.value)
        assertEquals(first.cacheKey, second.cacheKey)
        assertEquals("opt-out", assertIs<ReferenceTarget>(first.value).value)
    }


    @Test
    fun danglingWrongTypeAndCreationFailureRemainDistinctFailuresOrValues() {
        val fixture = fixture()

        val dangling = assertIs<WorkerDefinitionResolution.Failed>(
            fixture.resolver.resolve(ObjectReference.parse("MissingTarget"), location("DanglingHolder")))
        assertContains(dangling.message, "Unable to resolve 'MissingTarget'")

        val wrongType = assertIs<WorkerDefinitionResolution.Resolved>(
            fixture.resolver.resolve(
                ObjectReference.parse("NominalReferenceCreator"), location("WrongTypeHolder")))
        assertSame(NominalReferenceCreator, wrongType.value)

        val creationFailure = assertIs<WorkerDefinitionResolution.Failed>(
            fixture.resolver.resolve(
                ObjectReference.parse(
                    "test/job/reference/nominal-reference-target-test.yaml#FailingTarget"),
                location("FailingHolder")))
        assertContains(creationFailure.message, "Unable to create referenced object")
        assertContains(creationFailure.message, "reference target creation failure")
    }


    @Test
    fun compiledSnapshotAndCompatibilityKeyStayIsolatedFromLaterNotation() {
        val baseNotation = AutoTestUtils.readNotation()
        val base = fixture(baseNotation)
        val reference = ObjectReference.parse(
            "test/job/reference/nominal-reference-target-test.yaml#CrossTarget")

        val before = assertIs<WorkerDefinitionResolution.Resolved>(
            base.resolver.resolve(reference, location("CrossHolder")))

        val editedNotation = edit(
            baseNotation, crossTarget, "value", ScalarAttributeNotation("edited"))
        val edited = fixture(editedNotation)
        val after = assertIs<WorkerDefinitionResolution.Resolved>(
            edited.resolver.resolve(reference, location("CrossHolder")))
        val baseAgain = assertIs<WorkerDefinitionResolution.Resolved>(
            base.resolver.resolve(reference, location("CrossHolder")))

        assertEquals("cross", assertIs<ReferenceTarget>(before.value).value)
        assertSame(before.value, baseAgain.value)
        assertEquals(before.cacheKey, baseAgain.cacheKey)
        assertEquals("edited", assertIs<ReferenceTarget>(after.value).value)
        assertNotEquals(before.cacheKey, after.cacheKey)

        val unrelatedNotation = edit(
            baseNotation, location("SameTarget"), "value", ScalarAttributeNotation("unrelated"))
        val unrelated = fixture(unrelatedNotation)
        val unrelatedResolution = assertIs<WorkerDefinitionResolution.Resolved>(
            unrelated.resolver.resolve(reference, location("CrossHolder")))
        assertEquals(before.cacheKey, unrelatedResolution.cacheKey)
    }


    @Test
    fun definitionDependencyDigestChangesWithHostedLogicDefinition() {
        val target = ObjectLocation.parse("test/datasource/logic/dated-sales-test.yaml#main")
        val step = ObjectLocation.parse("test/datasource/logic/dated-sales-test.yaml#main.steps/Resolve")
        val baseNotation = AutoTestUtils.readNotation()
        val before = fixture(baseNotation).resolver.definitionDependencyDigest(target)

        val editedNotation = edit(
            baseNotation, step, "code", ScalarAttributeNotation("emptyList()"))
        val after = fixture(editedNotation).resolver.definitionDependencyDigest(target)

        assertNotEquals(before, after)
    }


    private fun fixture(
        notation: GraphNotation = AutoTestUtils.readNotation()
    ): Fixture {
        if (!::context.isInitialized) {
            context = KzenAutoContext.forTest()
        }
        val definition = AutoTestUtils.graphDefinitionAttempt(notation).transitiveSuccessful
        val runInstance = GraphCreator.createGraph(
            definition.filterTransitive(hostDocument), context.graphEnvironment)
        return Fixture(
            definition,
            runInstance,
            WorkerDefinitionContext(definition, runInstance, context.graphEnvironment))
    }


    private fun location(name: String): ObjectLocation {
        return ObjectLocation(hostDocument, ObjectPath.parse(name))
    }


    private fun edit(
        notation: GraphNotation,
        target: ObjectLocation,
        attribute: String,
        value: AttributeNotation
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(target, AttributeName(attribute), value))
            .graphNotation
    }


    private data class Fixture(
        val definition: GraphDefinition,
        val runInstance: GraphInstance,
        val resolver: WorkerDefinitionContext
    )
}
