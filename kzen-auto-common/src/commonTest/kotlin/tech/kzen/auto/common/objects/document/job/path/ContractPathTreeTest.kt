package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue


/** The picker's offered candidates level by level: fields, `[*]` elements, map key / value, collapsed references. */
class ContractPathTreeTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val number = DataType.Scalar(ScalarKind.Floating(64))
    private val execution = DataType.Record(listOf(DataField(FieldId("price"), number)))
    private val orderId = DefinitionId("Order")
    private val order = DataType.Record(listOf(
        DataField(FieldId("symbol"), text),
        DataField(FieldId("executions"), DataType.Listing(execution)),
        DataField(FieldId("notes"), DataType.Listing(text)),
        DataField(FieldId("tags"), DataType.Mapping(text, number)),
        DataField(FieldId("parent"), DataType.Reference(orderId, nullable = true))))
    private val contract = DataContract(order, emptyMap(), mapOf(orderId to order))


    @Test
    fun offersFieldsThenElementsThenEntriesAndExpandsReferencesOnDemand() {
        val roots = ContractPathTree.roots(contract)
        assertEquals(listOf("symbol", "executions", "notes", "tags", "parent"), roots.map { it.label })
        assertTrue(roots[0].selectable && !roots[0].expandable)
        assertEquals(ContractPathTree.Kind.List, roots[1].kind)
        assertEquals(ContractPathTree.Kind.Reference("Order"), roots[4].kind)
        assertTrue(roots[4].expandable, "a reference is collapsed until expanded")

        val executionChildren = ContractPathTree.children(contract, roots[1])
        assertEquals(listOf("executions[*].price"), executionChildren.map { it.path.asString() })
        assertIs<ContractPathTree.Kind.Leaf>(executionChildren.single().kind)

        val notesChildren = ContractPathTree.children(contract, roots[2])
        assertEquals(listOf("notes[*]"), notesChildren.map { it.path.asString() })
        assertTrue(notesChildren.single().selectable, "a scalar element is the leaf itself")

        val tagsChildren = ContractPathTree.children(contract, roots[3])
        assertEquals(listOf("tags[*].key", "tags[*].value"), tagsChildren.map { it.path.asString() })

        val parentChildren = ContractPathTree.children(contract, roots[4])
        assertEquals(listOf("parent.symbol", "parent.executions", "parent.notes", "parent.tags", "parent.parent"),
            parentChildren.map { it.path.asString() })
        assertEquals(ContractPathTree.Kind.Reference("Order"), parentChildren[4].kind)
    }


    @Test
    fun leavesAndUnsupportedNodesHaveNoChildren() {
        val roots = ContractPathTree.roots(contract)
        assertEquals(emptyList(), ContractPathTree.children(contract, roots[0]))
        val dynamic = DataContract(DataType.Record(listOf(DataField(FieldId("handle"), DataType.Dynamic()))))
        val handle = ContractPathTree.roots(dynamic).single()
        assertIs<ContractPathTree.Kind.Unsupported>(handle.kind)
        assertTrue(!handle.selectable && !handle.expandable)
        assertEquals(emptyList(), ContractPathTree.roots(DataContract(text)))
    }
}
