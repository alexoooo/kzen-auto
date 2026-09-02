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
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.exec.data.value.DataState
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import java.math.BigDecimal
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


class JobExpressionCompilerTest {
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


    private fun recordContract(): DataContract = DataContract(DataType.Record(listOf(
        DataField(FieldId("key"), DataType.Scalar(ScalarKind.Text)),
        DataField(FieldId("amount"), DataType.Scalar(ScalarKind.Decimal)))))
}
