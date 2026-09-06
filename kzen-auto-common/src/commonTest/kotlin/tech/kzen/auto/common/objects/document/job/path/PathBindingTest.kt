package tech.kzen.auto.common.objects.document.job.path

import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


/**
 * The E8 binding rules on JVM and JS: parsing and default names, existence, `[*]` on lists and maps with
 * `key` / `value`, scalar leaves, aliases and collisions, and a recursive contract bound finitely.
 */
class PathBindingTest {
    private val text = DataType.Scalar(ScalarKind.Text)
    private val number = DataType.Scalar(ScalarKind.Floating(64))

    private val trade = DataType.Record(listOf(DataField(FieldId("venue"), text)), nullable = true)
    private val execution = DataType.Record(listOf(
        DataField(FieldId("price"), number),
        DataField(FieldId("trade"), trade)))
    private val orderId = DefinitionId("Order")
    private val order = DataType.Record(listOf(
        DataField(FieldId("symbol"), text),
        DataField(FieldId("executions"), DataType.Listing(execution)),
        DataField(FieldId("tags"), DataType.Mapping(text, number)),
        DataField(FieldId("legs"), DataType.Listing(DataType.Reference(orderId), nullable = true))))
    private val contract = DataContract(order, emptyMap(), mapOf(orderId to order))


    @Test
    fun parsesAndNamesByConvention() {
        val path = ProjectionPath.parse("executions[*].trade.venue")
        assertEquals("executions[*].trade.venue", path.asString())
        assertEquals("executions.trade.venue", path.defaultOutputName())
        assertTrue(path.unnests)
        assertEquals("symbol", ProjectionPath.parse("symbol").defaultOutputName())
        assertFailsWith<IllegalArgumentException> { ProjectionPath.parse("executions[0].price") }
        assertFailsWith<IllegalArgumentException> { ProjectionPath.parse("") }
    }


    @Test
    fun bindsLeavesListsMapsAndRecursionFinitely() {
        val spec = spec("symbol", "executions[*].price", "executions[*].trade.venue",
            "tags[*].key", "tags[*].value", "legs[*].legs[*].symbol")
        val result = PathBinding.bind(spec, contract)
        assertTrue(result.isValid, result.errorMessage() ?: "")
        val record = assertNotNull(result.contract).structural as DataType.Record
        assertEquals(
            listOf("symbol", "executions.price", "executions.trade.venue", "tags.key", "tags.value", "legs.legs.symbol"),
            record.fields.map { it.id.name })
        assertTrue(record.fields.all { (it.type as DataType.Scalar).nullable }, "leaves are nullable through intermediates")
        assertEquals(ScalarKind.Floating(64), (record.fields[1].type as DataType.Scalar).kind)
        assertEquals(
            listOf(BoundStep.Field(FieldId("tags")), BoundStep.Entries, BoundStep.Key),
            result.paths[3].steps)
        assertEquals(listOf(BoundStep.Field(FieldId("executions")), BoundStep.Elements), result.paths[1].iterationKey)
        assertEquals(emptyList(), result.paths[0].iterationKey)
    }


    @Test
    fun aliasesOverrideAndCollisionsNameBothPaths() {
        val aliased = PathProjectionSpec(listOf(
            PathProjectionEntry(ProjectionPath.parse("executions[*].price"), "px"),
            PathProjectionEntry(ProjectionPath.parse("symbol"))))
        assertEquals(listOf("px", "symbol"), PathBinding.bind(aliased, contract).paths.map { it.outputName })

        val colliding = PathProjectionSpec(listOf(
            PathProjectionEntry(ProjectionPath.parse("executions[*].price")),
            PathProjectionEntry(ProjectionPath.parse("symbol"), "executions.price")))
        val result = PathBinding.bind(colliding, contract)
        assertNull(result.contract)
        val error = assertNotNull(result.errors.singleOrNull())
        assertEquals("symbol", error.path.asString())
        assertTrue(error.message.contains("executions[*].price") && error.message.contains("alias"), error.message)
    }


    @Test
    fun rejectsUnknownFieldsNonScalarLeavesAndMisusedWildcards() {
        val result = PathBinding.bind(
            spec("executions[*].trade", "nope", "symbol[*]", "tags[*].price", "tags[*]"), contract)
        assertNull(result.contract)
        val messages = result.errors.associate { it.path.asString() to it.message }
        assertTrue(messages.getValue("executions[*].trade").contains("Formula"), messages.toString())
        assertTrue(messages.getValue("nope").contains("available: symbol"), messages.toString())
        assertTrue(messages.getValue("symbol[*]").contains("needs a list or map"), messages.toString())
        assertTrue(messages.getValue("tags[*].price").contains("'key' or 'value'"), messages.toString())
        assertTrue(messages.getValue("tags[*]").contains("must continue"), messages.toString())
    }


    private fun spec(vararg paths: String): PathProjectionSpec =
        PathProjectionSpec(paths.map { PathProjectionEntry(ProjectionPath.parse(it)) })
}
