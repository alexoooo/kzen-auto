package tech.kzen.auto.server.service.impl

import org.junit.Test
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
 * Unit coverage for [LinkedLogicDocuments]'s notation-driven discovery: a RunStep's weak `instructions` link
 * pulls the callee document into the set; intra-document `is: ObjectLocation` references (step references,
 * selfLocation) contribute nothing; and mutual hosting terminates via the visited-set cycle guard.
 */
class LinkedLogicDocumentsTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val runParent = DocumentPath.parse("test/script-engine-run-test.yaml")
    private val runChild = DocumentPath.parse("test/script-engine-child-test.yaml")
    private val ifDocument = DocumentPath.parse("test/script-engine-if-test.yaml")
    private val cycleA = DocumentPath.parse("test/script-linked-cycle-a-test.yaml")
    private val cycleB = DocumentPath.parse("test/script-linked-cycle-b-test.yaml")

    private val graphStructure: GraphStructure by lazy {
        val graphNotation = AutoTestUtils.readNotation()
        GraphStructure(graphNotation, AutoTestUtils.graphMetadata(graphNotation))
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runStepInstructionsLinkPullsCalleeDocumentIntoSet() {
        assertEquals(
            setOf(runParent, runChild),
            LinkedLogicDocuments.linkedDocumentPaths(graphStructure, runParent))
    }


    @Test
    fun intraDocumentObjectLocationReferencesContributeNothing() {
        // IfStep.condition / step references are `is: ObjectLocation` but target the SAME document
        assertEquals(
            setOf(ifDocument),
            LinkedLogicDocuments.linkedDocumentPaths(graphStructure, ifDocument))
    }


    @Test
    fun leafDocumentIsJustItself() {
        assertEquals(
            setOf(runChild),
            LinkedLogicDocuments.linkedDocumentPaths(graphStructure, runChild))
    }


    @Test
    fun mutualHostingCycleTerminates() {
        assertEquals(
            setOf(cycleA, cycleB),
            LinkedLogicDocuments.linkedDocumentPaths(graphStructure, cycleA))
        assertEquals(
            setOf(cycleB, cycleA),
            LinkedLogicDocuments.linkedDocumentPaths(graphStructure, cycleB))
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
