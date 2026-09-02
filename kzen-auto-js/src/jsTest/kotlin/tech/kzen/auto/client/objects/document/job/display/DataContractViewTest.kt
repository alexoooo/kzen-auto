package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.lib.common.exec.data.shape.DataShape
import tech.kzen.lib.common.exec.data.shape.DiagnosticSeverity
import tech.kzen.lib.common.exec.data.shape.SampleCoverage
import tech.kzen.lib.common.exec.data.shape.SchemaDiagnostic
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataField
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.FieldId
import tech.kzen.lib.common.exec.data.type.ScalarKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DataContractViewTest {
    @Test
    fun fiveStatesHaveDistinctSummaries() {
        assertEquals("Loading…", DataContractPresentation.of(DataContractDisplay.Loading).summary)
        assertEquals("Unavailable", DataContractPresentation.of(DataContractDisplay.Unavailable).summary)
        assertEquals("Error", DataContractPresentation.of(DataContractDisplay.Error("failed")).summary)
        assertEquals("Dynamic", DataContractPresentation.of(DataContractDisplay.Dynamic).summary)
        assertEquals(
            "Record · 1 field",
            DataContractPresentation.of(DataContractDisplay.Contract(recordContract())).summary)
    }


    @Test
    fun detailsRetainTypesOptionalityShapeCoverageAndDiagnostics() {
        val contract = recordContract()
        val shape = DataShape(
            contract,
            ShapeProvenance.ProviderReported,
            ShapeStability.Provisional(SampleCoverage(12, 256, complete = false)),
            listOf(SchemaDiagnostic(
                DiagnosticSeverity.Warning,
                "sample",
                "sampled values only",
                "/field:value#0")))

        val details = DataContractPresentation
            .of(DataContractDisplay.Contract(contract, shape))
            .details

        assertTrue(details.any { it.contains("value: Decimal · nullable · optional") })
        assertTrue(details.any { it == "provenance: ProviderReported" })
        assertTrue(details.any { it == "stability: Provisional · 12 items · 256 bytes · partial" })
        assertTrue(details.any { it.contains("Warning: sample at /field:value#0") })
    }


    private fun recordContract(): DataContract = DataContract(DataType.Record(listOf(
        DataField(
            FieldId("value"),
            DataType.Scalar(ScalarKind.Decimal, nullable = true),
            optional = true))))
}
