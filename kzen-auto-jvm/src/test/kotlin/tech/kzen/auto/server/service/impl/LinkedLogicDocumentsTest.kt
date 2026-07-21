package tech.kzen.auto.server.service.impl

import org.junit.Test
import tech.kzen.auto.common.paradigm.logic.LogicCallGraph
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


/**
 * Coverage for the live-edit migration signal, and for [LogicCallGraph] discovery against the REAL step
 * archetypes (the traversal semantics themselves are covered by fixture in `LogicCallGraphTest`, which lives
 * in kzen-auto-common alongside the code — what only kzen-auto-jvm can pin is that `RunStep.instructions` as
 * actually declared in `auto-jvm/script/script-jvm.yaml` produces the edge).
 */
class LinkedLogicDocumentsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val runParent = DocumentPath.parse("test/script-engine-run-test.yaml")
    private val runChild = DocumentPath.parse("test/script-engine-child-test.yaml")
    private val flowRunHost = DocumentPath.parse("test/flow-run-test.yaml")
    private val jobRunHost = DocumentPath.parse("test/job-run-host-test.yaml")
    private val ifDocument = DocumentPath.parse("test/script-engine-if-test.yaml")
    private val cycleA = DocumentPath.parse("test/script-linked-cycle-a-test.yaml")
    private val cycleB = DocumentPath.parse("test/script-linked-cycle-b-test.yaml")

    private val graphStructure: GraphStructure by lazy {
        val graphNotation = AutoTestUtils.readNotation()
        GraphStructure(graphNotation, AutoTestUtils.graphMetadata(graphNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runStepInstructionsLinkIsDiscovered() {
        assertEquals(
            setOf(runChild),
            LogicCallGraph.transitiveCallees(graphStructure, runParent))
    }


    @Test
    fun everyParadigmsHostingArchetypeYieldsTheSameEdge() {
        // The one child Script is hosted from all three paradigms - Script RunStep, Flow RunLogic, Job
        // RunWorker - and each is found without naming a step type: the edge comes from the `is: ObjectLocation`
        // metadata their `instructions` attributes share.
        assertEquals(
            setOf(runParent, flowRunHost, jobRunHost),
            LogicCallGraph.transitiveCallers(graphStructure, runChild))
    }


    @Test
    fun intraDocumentObjectLocationReferencesContributeNothing() {
        // IfStep.condition / step references are `is: ObjectLocation` but target the SAME document
        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallees(graphStructure, ifDocument))
    }


    @Test
    fun leafDocumentCallsNothing() {
        assertEquals(
            emptySet<DocumentPath>(),
            LogicCallGraph.transitiveCallees(graphStructure, runChild))
    }


    @Test
    fun mutualHostingCycleTerminates() {
        assertEquals(
            setOf(cycleB, cycleA),
            LogicCallGraph.transitiveCallees(graphStructure, cycleA))
        assertEquals(
            setOf(cycleA, cycleB),
            LogicCallGraph.transitiveCallees(graphStructure, cycleB))
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The widened signal is precise: a callee-document edit changes the caller's digest (that's the point of
    // the phase), while an edit in a document the caller does NOT link leaves it unchanged (no spurious
    // migration on unrelated edits).
    @Test
    fun signalSeesCalleeEditsButIgnoresUnlinkedDocumentEdits() {
        val baseNotation = AutoTestUtils.readNotation()
        val baseline = signalDigest(baseNotation)

        val calleeEdited = edit(
            baseNotation,
            ObjectLocation(runChild, ObjectPath.parse("main.steps/Plus")),
            "code", "number + 100")
        assertNotEquals(baseline, signalDigest(calleeEdited), "callee edit must change the caller's signal")

        val unlinkedEdited = edit(
            baseNotation,
            ObjectLocation(ifDocument, ObjectPath.parse("main.steps/Flag")),
            "code", "false")
        assertEquals(baseline, signalDigest(unlinkedEdited), "unlinked-document edit must not change the signal")
    }


    private fun signalDigest(graphNotation: GraphNotation): Digest {
        val attempt = AutoTestUtils.graphDefinitionAttempt(graphNotation)
        return LinkedLogicDocuments.transitiveDigest(
            attempt.transitiveSuccessful, attempt.graphStructure, runParent)
    }


    private fun edit(
        notation: GraphNotation,
        location: ObjectLocation,
        attribute: String,
        value: String
    ): GraphNotation {
        return NotationReducer()
            .applyStructural(
                notation,
                UpsertAttributeCommand(
                    location, AttributeName(attribute), ScalarAttributeNotation(value)))
            .graphNotation
    }
}
