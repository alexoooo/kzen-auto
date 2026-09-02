package tech.kzen.auto.client.objects.document.job.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import tech.kzen.auto.client.objects.document.job.display.DataContractDisplay
import tech.kzen.auto.common.data.schema.DataShape
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.shape.ShapeStability
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType


class DataSourceInspectionDisplayTest {
    @Test
    fun keepsAllFiveInspectionStatesDistinct() {
        assertEquals(
            DataContractDisplay.Loading,
            DataSourceInspectionDisplay.of(null, null, pending = true))
        assertEquals(
            DataContractDisplay.Unavailable,
            DataSourceInspectionDisplay.of(null, null, pending = false))
        assertIs<DataContractDisplay.Error>(DataSourceInspectionDisplay.of(
            DataSourceResolveStore.State(false, null, "resolve failed"), null, pending = false))

        val dynamic = DataShapeResult.Observed(DataShape(
            DataContract(DataType.Dynamic()), ShapeProvenance.Declared, ShapeStability.Stable))
        assertEquals(
            DataContractDisplay.Dynamic,
            DataSourceInspectionDisplay.of(
                null,
                DataSourceShapeStore.State(emptyMap(), dynamic),
                pending = false))

        val contract = DataContract(DataType.Record(emptyList()))
        val observed = DataShapeResult.Observed(DataShape(
            contract, ShapeProvenance.Declared, ShapeStability.Stable))
        assertIs<DataContractDisplay.Contract>(DataSourceInspectionDisplay.of(
            null,
            DataSourceShapeStore.State(emptyMap(), observed),
            pending = false))
    }
}
