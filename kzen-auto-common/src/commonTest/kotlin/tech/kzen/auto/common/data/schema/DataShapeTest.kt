package tech.kzen.auto.common.data.schema

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.lib.common.exec.data.shape.ShapeProvenance
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.DataTypePath
import tech.kzen.lib.common.exec.data.type.ScalarKind
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


class DataShapeTest {
    @Test
    fun legacyTabularBridgePreservesOrderedDuplicateLabels() {
        val shape = LegacyDataShapeBridge.tabular(
            HeaderListing.of(listOf("alpha", "alpha", "beta")))

        val record = assertIs<DataType.Record>(shape.itemType.structural)
        assertEquals(
            listOf("alpha" to 0, "alpha" to 1, "beta" to 0),
            record.fields.map { it.id.name to it.id.occurrence })
        assertEquals(ScalarKind.Text, assertIs<DataType.Scalar>(record.fields[0].type).kind)
        assertEquals(
            listOf("alpha", "alpha", "beta"),
            LegacyDataShapeBridge.headerOrNull(shape)!!.values.map { it.text })
        assertEquals(ShapeProvenance.ProviderReported, shape.provenance)
    }


    @Test
    fun legacyPayloadBridgePreservesNativeIdentityAndBothCodecsRoundTrip() {
        val metadata = TypeMetadata(
            ClassName("example.Payload"),
            listOf(TypeMetadata.string),
            true)
        val shape = LegacyDataShapeBridge.payload(metadata)

        assertEquals(metadata, shape.itemType.nativeByPath[DataTypePath.root])
        assertEquals(metadata, LegacyDataShapeBridge.legacyPayloadType(shape))
        assertEquals(shape, DataShape.ofExecutionValue(shape.asExecutionValue()))
        assertEquals(shape, Json.decodeFromString<DataShape>(Json.encodeToString(shape)))
    }


    @Test
    fun runtimeUnknownIsStructuralRatherThanASecondShapeKind() {
        val shape = LegacyDataShapeBridge.runtimeUnknown()

        assertIs<DataType.Dynamic>(shape.itemType.structural)
        assertEquals(ShapeProvenance.RuntimeOnly, shape.provenance)
        assertEquals(null, LegacyDataShapeBridge.headerOrNull(shape))
    }
}
