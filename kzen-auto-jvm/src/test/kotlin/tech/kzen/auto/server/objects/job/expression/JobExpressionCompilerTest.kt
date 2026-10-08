package tech.kzen.auto.server.objects.job.expression

import org.junit.Test
import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.service.compile.CachedKotlinCompiler
import tech.kzen.auto.server.service.compile.KotlinSyntaxValidator
import tech.kzen.auto.server.service.compile.ScriptKotlinCompiler
import tech.kzen.auto.server.util.ClassLoaderUtils
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.LongExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DefinitionId
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.MetadataContract
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.exec.data.value.LiteralDataValues
import tech.kzen.lib.common.exec.data.value.ValueMetadata
import tech.kzen.lib.common.exec.data.value.recordOf
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class JobExpressionCompilerTest {
    /** An ordinary class — not a data class — so the bean convention, not component order, names its columns. */
    class PlainRow(val id: Int, val name: String)

    class Conflicting {
        @JvmField val value: Int = 1
        fun getValue(): String = "one"
    }

    data class Chain(val label: String, val next: Chain?)

    private val compiler = JobExpressionCompiler(
        CachedKotlinCompiler(
            ScriptKotlinCompiler(),
            WorkUtils(Path.of("../work/${JobExpressionCompilerTest::class.simpleName}"))),
        KotlinSyntaxValidator())
    private val classLoader = ClassLoaderUtils.dynamicParentClassLoader()


    @Test
    fun generatedRecordAccessIsOrdinalAndTransportNeutral() {
        val contract = recordContract()
        val code = compiler.generate("amount", "amount", contract, TypeMetadata.anyNullable)

        assertTrue(code.sourceText.contains("field(0)"))
        assertTrue(code.sourceText.contains("field(1)"))
        assertTrue(code.sourceText.contains("fun key("))
        assertFalse(code.sourceText.contains("RecordHeaderIndex"))
        assertFalse(code.sourceText.contains("provider"))
        assertFalse(code.sourceText.contains("compression"))
        assertEquals(
            code.signature(),
            compiler.generate("amount", "amount", recordContract(), TypeMetadata.anyNullable).signature())
    }


    @Test
    fun decimalAccessorAndInferredContractStayExact() {
        val contract = recordContract()
        val attempt = compiler.compile(
            "amount",
            "amount + BigDecimal(\"0.00000000000000000001\")",
            contract,
            TypeMetadata.anyNullable,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)
        assertEquals(DataType.Scalar(ScalarKind.Decimal), compiled.contract.structural)

        val original = "12345678901234567890.12345678901234567890"
        val value = JobDataValues.projectedRecord(
            contract,
            FlatFileRecord.of("key", original),
            listOf(DataState.Present, DataState.Present))
        val result = compiled.expression.evaluate(
            null,
            value,
            JobDataValues.projection(value))

        assertEquals(BigDecimal("12345678901234567890.12345678901234567891"), result)
    }


    @Test
    fun staticStreamClassificationRetainsExactElementContract() {
        val attempt = compiler.compile(
            "decimalStream",
            "listOf(BigDecimal(\"12345678901234567890.12345678901234567890\"))",
            DataContract(DataType.Dynamic()),
            TypeMetadata.unit,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)

        assertTrue(compiled.streams)
        assertEquals(
            DataType.Scalar(ScalarKind.Decimal),
            assertNotNull(compiled.streamElementContract).structural)
    }


    @Test
    fun plainObjectStreamShowsItsColumnsBeforeExecutionAndEmitsThem() {
        val attempt = compiler.compile(
            "rows",
            "listOf(${PlainRow::class.qualifiedName}(1, \"a\"))",
            DataContract(DataType.Dynamic()),
            TypeMetadata.unit,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)

        assertTrue(compiled.streams)
        val designTime = assertIs<DataType.Record>(assertNotNull(compiled.streamElementContract).structural)
        assertEquals(listOf("id", "name"), designTime.fields.map { it.id.name })

        val emitted = JobDataValues.lift(PlainRow(1, "a"))
        assertEquals(designTime, emitted.type, "the run emits the columns the editor showed")
        assertEquals(1L, emitted.access.readLong(emitted.access.field(emitted.root, FieldId("id"))))

        val single = assertNotNull(compiler.compile(
            "row", "${PlainRow::class.qualifiedName}(2, \"b\")", DataContract(DataType.Dynamic()), TypeMetadata.unit, classLoader).compiled)
        assertFalse(single.streams)
        assertEquals(designTime, single.contract.structural)

        val refused = compiler.compile(
            "conflict", "${Conflicting::class.qualifiedName}()", DataContract(DataType.Dynamic()), TypeMetadata.unit, classLoader)
        assertTrue(assertNotNull(refused.error).contains("Conflicting"), refused.error)
    }


    @Test
    fun recursiveClassGivesAFiniteContractAColdEditorCanTraverse() {
        val attempt = compiler.compile(
            "chain",
            "${Chain::class.qualifiedName}(\"a\", ${Chain::class.qualifiedName}(\"b\", null))",
            DataContract(DataType.Dynamic()),
            TypeMetadata.unit,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)

        // Finite: the recursive occurrence is a named reference with one definition
        val root = assertIs<DataType.Record>(compiled.contract.structural)
        val next = root.fields.single { it.id.name == "next" }.type
        assertIs<DataType.Reference>(next)
        assertEquals(setOf(DefinitionId(Chain::class.qualifiedName!!)), compiled.contract.definitions.keys)

        // Traversable without running the source: each step expands one level
        val level1 = compiled.contract.child(DataPathSegment.Field(FieldId("next")))
        assertIs<DataType.Record>(level1.structural)
        val level2 = level1.child(DataPathSegment.Field(FieldId("next")))
        assertIs<DataType.Record>(level2.structural)
        assertEquals(compiled.contract.definitions, level2.definitions)

        // The wire form a client receives round-trips with its definitions
        val decoded = DataContract.ofExecutionValue(compiled.contract.asExecutionValue())
        assertEquals(compiled.contract, decoded)

        // And the run's value satisfies the design-time contract
        val emitted = JobDataValues.lift(Chain("a", Chain("b", null)), compiled.contract)
        assertEquals("b", emitted.access.readText(emitted.access.field(emitted.access.field(emitted.root, FieldId("next")), FieldId("label"))))
    }


    @Test
    fun dynamicContractRequiresAndRunsExplicitKeyedAccess() {
        val dynamic = DataContract(DataType.Dynamic())
        assertTrue(compiler.generate(
            "dynamic", "key(\"amount\")", dynamic, TypeMetadata.anyNullable)
            .sourceText.contains("fun key("))
        val attempt = compiler.compile(
            "dynamic",
            "(key(\"amount\") as BigDecimal) * BigDecimal(\"2\")",
            dynamic,
            TypeMetadata.anyNullable,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)
        assertIs<DataType.Scalar>(compiled.contract.structural)

        val value = JobDataValues.lift(linkedMapOf("amount" to BigDecimal("2.5")))
        assertEquals(BigDecimal("5.0"), compiled.expression.evaluate(null, value, null))
    }


    @Test
    fun concreteRecordRetainsBareAndExplicitKeyedAccessWhenAFieldIsNamedKey() {
        val contract = recordContract()
        val attempt = compiler.compile(
            "recordKey",
            "key + \"-\" + key(\"amount\")",
            contract,
            TypeMetadata.anyNullable,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)
        val value = JobDataValues.projectedRecord(
            contract,
            FlatFileRecord.of("label", "2.5"),
            listOf(DataState.Present, DataState.Present))

        assertEquals(
            "label-2.5",
            compiled.expression.evaluate(null, value, JobDataValues.projection(value)))
    }


    @Test
    fun signedAndUnsignedIntegerAccessorsMatchBoundaryValues() {
        val contract = DataContract(DataType.Record(listOf(
            DataField(FieldId("signed"), DataType.Scalar(ScalarKind.Integer(8))),
            DataField(FieldId("unsigned8"), DataType.Scalar(ScalarKind.Integer(8, signed = false))),
            DataField(FieldId("unsigned32"), DataType.Scalar(ScalarKind.Integer(32, signed = false))))))
        val attempt = compiler.compile(
            "integerBoundaries",
            "signed.toLong() + unsigned8.toLong() + unsigned32.toLong()",
            contract,
            TypeMetadata.anyNullable,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)
        val value = JobDataValues.projectedRecord(
            contract,
            FlatFileRecord.of("-128", "255", "4294967295"),
            List(3) { DataState.Present })

        assertEquals(
            4_294_967_422L,
            compiled.expression.evaluate(null, value, JobDataValues.projection(value)))
        assertEquals(
            LongExecutionValue(4_294_967_295L),
            JobExpressionValues.scalar(UInt.MAX_VALUE, DataType.Scalar(
                ScalarKind.Integer(32, signed = false))).second)
    }


    @Test
    fun unsigned64BoundaryIsRejectedPrecisely() {
        val attempt = compiler.compile(
            "unsupported",
            "value",
            DataContract(DataType.Scalar(ScalarKind.Integer(64, signed = false))),
            TypeMetadata.anyNullable,
            classLoader)

        assertEquals(null, attempt.compiled)
        assertEquals(
            "Job expression boundary does not support unsigned 64-bit integers",
            attempt.error)
    }


    @Test
    fun recursiveMetadataIsOneClassThatRefersToItself() {
        val contract = DataContract(DataType.Dynamic()).withMetadata(recursiveMetadata())
        val code = compiler.generate("lineage", "origin.name", contract, TypeMetadata.anyNullable)

        // The root, and one class for the definition however deep the value goes
        assertEquals(listOf("Meta0", "Meta1"), metadataClassNames(code.sourceText))
        assertTrue(code.sourceText.contains("val origin: Meta1 get()"), code.sourceText)
        assertTrue(code.sourceText.contains("val parent: Meta1? get()"), code.sourceText)

        // The compile cache key depends on the contract's content, not on how it was built or decoded
        val decoded = DataContract.ofExecutionValue(contract.asExecutionValue())
        assertEquals(
            code.signature(),
            compiler.generate("lineage", "origin.name", decoded, TypeMetadata.anyNullable).signature())
    }


    @Test
    fun recursiveMetadataEvaluatesTypedAndKeyedThroughTwoLevels() {
        val contract = DataContract(DataType.Dynamic()).withMetadata(recursiveMetadata())
        val attempt = compiler.compile(
            "lineage",
            "listOf(origin.name, origin.parent?.name, meta.origin.parent?.parent?.name, " +
                    "origin.parent?.parent?.get(\"name\"), origin.parent?.parent?.parent?.name ?: \"-\")" +
                    ".joinToString(\"/\")",
            contract,
            TypeMetadata.anyNullable,
            classLoader)
        val compiled = assertNotNull(attempt.compiled, attempt.error)
        assertEquals(DataType.Scalar(ScalarKind.Text), compiled.contract.structural)

        val value = JobDataValues.lift(linkedMapOf("amount" to BigDecimal.ONE))
            .withMetadata(lineageMetadata())
        assertEquals("c/b/a/a/-", compiled.expression.evaluate(null, value, null))
    }


    @Test
    fun mutuallyRecursiveMetadataIsOneClassPerDefinition() {
        val folderId = DefinitionId("test.Folder")
        val fileId = DefinitionId("test.File")
        val name = DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text))
        val metadata = MetadataContract(
            DataType.Record(listOf(DataField(FieldId("file"), DataType.Reference(fileId)))),
            mapOf(
                folderId to DataType.Record(listOf(
                    name, DataField(FieldId("readme"), DataType.Reference(fileId, nullable = true)))),
                fileId to DataType.Record(listOf(
                    name, DataField(FieldId("folder"), DataType.Reference(folderId, nullable = true))))))
        val contract = DataContract(DataType.Dynamic()).withMetadata(metadata)

        val attempt = compiler.compile(
            "readme", "file.folder?.readme?.folder?.name", contract, TypeMetadata.anyNullable, classLoader)

        val compiled = assertNotNull(attempt.compiled, attempt.error)
        assertEquals(DataType.Scalar(ScalarKind.Text, nullable = true), compiled.contract.structural)
        val source = compiler.generate(
            "readme", "file.folder?.readme?.folder?.name", contract, TypeMetadata.anyNullable).sourceText
        assertEquals(listOf("Meta0", "Meta1", "Meta2"), metadataClassNames(source))
        assertTrue(source.contains("val folder: Meta2? get()"), source)
        assertTrue(source.contains("val readme: Meta1? get()"), source)
    }


    @Test
    fun nestedMetadataRecordsWithoutDefinitionsKeepAClassEach() {
        val name = DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text))
        val file = DataType.Record(listOf(name))
        val member = DataType.Record(listOf(name, DataField(FieldId("parent"), file)))
        val contract = DataContract(DataType.Dynamic()).withMetadata(MetadataContract(DataType.Record(listOf(
            DataField(FieldId("parent"), member)))))

        val code = compiler.generate("names", "parent.parent.name", contract, TypeMetadata.anyNullable)

        assertEquals(listOf("Meta0", "Meta1", "Meta2"), metadataClassNames(code.sourceText))
        assertTrue(code.sourceText.contains("val parent: Meta1 get()"), code.sourceText)
        assertTrue(code.sourceText.contains("val parent: Meta2 get()"), code.sourceText)
    }


    private val nodeId = DefinitionId("test.Node")


    /** Metadata `origin: Node` with `Node(name: String, parent: Node?)`. */
    private fun recursiveMetadata(): MetadataContract = MetadataContract(
        DataType.Record(listOf(DataField(FieldId("origin"), DataType.Reference(nodeId)))),
        mapOf(nodeId to DataType.Record(listOf(
            DataField(FieldId("name"), DataType.Scalar(ScalarKind.Text)),
            DataField(FieldId("parent"), DataType.Reference(nodeId, nullable = true))))))


    /** `c`, whose parent is `b`, whose parent is `a`. */
    private fun lineageMetadata(): ValueMetadata {
        val a = recordOf("name" to "a", "parent" to null)
        val b = recordOf("name" to "b", "parent" to a)
        val c = recordOf("name" to "c", "parent" to b)
        return ValueMetadata.of(LiteralDataValues.lift(recordOf("origin" to c), recursiveMetadata().contract))
    }


    private fun metadataClassNames(source: String): List<String> =
        Regex("""class (Meta\d+)\(""").findAll(source).map { it.groupValues[1] }.toList()


    private fun recordContract(): DataContract = DataContract(DataType.Record(listOf(
        DataField(FieldId("key"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("amount"), DataType.Scalar(ScalarKind.Decimal)))))
}
