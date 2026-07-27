package tech.kzen.auto.server.objects.script

import org.junit.Test
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.AttributeLocation
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.ShiftObjectTreeCommand
import tech.kzen.lib.common.service.notation.NotationReducer
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Reordering an If chain's branches is a pure document-position move: one [ShiftObjectTreeCommand] over the
 * branch object, which carries its whole subtree (condition attribute + nested steps) contiguously and renames
 * NOTHING — so stable ids, breakpoints, React keys and expand state all survive a drag.
 *
 * This pins the index math the client's branch drag uses (IfStepDisplay's drop handler) at the notation level,
 * where it can be asserted without a browser: the target document index is derived from the sibling list with
 * the dragged subtree removed, exactly as the drop handler derives it.
 */
class ScriptBranchReorderTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val documentPath = DocumentPath.parse("test/script-nesting-test.yaml")
    private val ifPath = ObjectPath.parse("main.steps/TopIf")
    private val branchesAttribute = AttributePath.ofName(AttributeName("branches"))


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun draggingTheFirstBranchPastTheSecondSwapsThemAndKeepsEachSubtreeContiguous() {
        val before = AutoTestUtils.readNotation()

        assertEquals(
            listOf("main.steps/TopIf.branches/Branch", "main.steps/TopIf.branches/Branch 2"),
            branchPaths(before))

        val dragged = ObjectPath.parse("main.steps/TopIf.branches/Branch")
        val after = NotationReducer()
            .applyStructural(
                before,
                ShiftObjectTreeCommand(
                    ObjectLocation(documentPath, dragged),
                    PositionRelation.at(dropIndexPastLastSibling(before, dragged))))
            .graphNotation

        assertEquals(
            listOf("main.steps/TopIf.branches/Branch 2", "main.steps/TopIf.branches/Branch"),
            branchPaths(after),
            "branch order is document order, so the shift alone reorders the chain")

        // The moved branch's own step travels with it, immediately after it — a branch that split across its
        // sibling would put its steps in the wrong section.
        val objectPaths = objectPaths(after)
        val branchIndex = objectPaths.indexOf("main.steps/TopIf.branches/Branch")
        assertEquals(
            "main.steps/TopIf.branches/Branch.steps/ThenStep",
            objectPaths[branchIndex + 1])

        // ... and the branch it moved past is likewise still contiguous, ahead of it.
        val otherIndex = objectPaths.indexOf("main.steps/TopIf.branches/Branch 2")
        assertEquals(
            "main.steps/TopIf.branches/Branch 2.steps/ElseIfStep",
            objectPaths[otherIndex + 1])
        assertTrue(otherIndex < branchIndex)

        // Nothing was renamed, so every object path in the document survives the move.
        assertEquals(objectPaths(before).toSet(), objectPaths.toSet())
    }


    //-----------------------------------------------------------------------------------------------------------------
    // The client's drop math for "past the last sibling": with the dragged subtree removed, land right after the
    // last remaining sibling's own subtree.
    private fun dropIndexPastLastSibling(graphNotation: GraphNotation, dragged: ObjectPath): Int {
        val remaining = graphNotation.documents[documentPath]!!
            .objects
            .notations
            .map
            .keys
            .filter { it != dragged && ! it.startsWith(dragged) }

        val lastSibling = ScriptConventions
            .orderedDirectChildLocations(
                graphNotation, AttributeLocation(ObjectLocation(documentPath, ifPath), branchesAttribute))
            .map { it.objectPath }
            .last { it != dragged }

        return remaining.indexOfLast { it == lastSibling || it.startsWith(lastSibling) } + 1
    }


    private fun branchPaths(graphNotation: GraphNotation): List<String> {
        return ScriptConventions
            .orderedDirectChildLocations(
                graphNotation, AttributeLocation(ObjectLocation(documentPath, ifPath), branchesAttribute))
            .map { it.objectPath.asString() }
    }


    private fun objectPaths(graphNotation: GraphNotation): List<String> {
        return graphNotation.documents[documentPath]!!
            .objects
            .notations
            .map
            .keys
            .map { it.asString() }
    }
}
