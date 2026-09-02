package tech.kzen.auto.common.data.model

import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class DataModelExecutionValueTest {
    private val attributes = linkedMapOf(
        "zeta" to "last",
        "alpha" to "first",
        "middle" to "between"
    )

    private val sourcedRef = DataRef(
        DataSourceId("source-1"),
        "orders?day=2026-08-23",
        attributes
    )

    private val sourcedPart = testDataPart(
        DataRole("reference"),
        sourcedRef)

    private val plainPart = testDataPart(DataRole.main, DataRef(null, "/data/main.csv"))
    private val unit = DataUnit(attributes, listOf(sourcedPart, plainPart))
    private val manifest = DataManifest(listOf(unit))


    @Test
    fun modelValuesRoundTrip() {
        assertEquals(sourcedRef, DataRef.ofExecutionValue(sourcedRef.asExecutionValue()))
        assertEquals(sourcedPart, DataPart.ofExecutionValue(sourcedPart.asExecutionValue()))
        assertEquals(unit, DataUnit.ofExecutionValue(unit.asExecutionValue()))
        assertEquals(manifest, DataManifest.ofExecutionValue(manifest.asExecutionValue()))

        val diagnostic = DataDiagnostic(DataDiagnostic.unsupported, "Unsupported extension")
        assertEquals(diagnostic, DataDiagnostic.ofExecutionValue(diagnostic.asExecutionValue()))

        val result = DataResolveResult(
            manifest,
            listOf(
                diagnostic,
                DataDiagnostic(DataDiagnostic.skipped, "Skipped empty input")
            )
        )
        assertEquals(result, DataResolveResult.ofExecutionValue(result.asExecutionValue()))
    }


    @Test
    fun nullableValuesRoundTrip() {
        val ref = DataRef(null, "/data/plain.csv")
        val part = testDataPart(DataRole.main, ref)

        assertEquals(ref, DataRef.ofExecutionValue(ref.asExecutionValue()))
        assertEquals(part, DataPart.ofExecutionValue(part.asExecutionValue()))
    }


    @Test
    fun attributeDisplayOrderSurvives() {
        val decodedRef = DataRef.ofExecutionValue(sourcedRef.asExecutionValue())
        val decodedUnit = DataUnit.ofExecutionValue(unit.asExecutionValue())

        assertEquals(attributes.keys.toList(), decodedRef.attributes.keys.toList())
        assertEquals(attributes.keys.toList(), decodedUnit.attributes.keys.toList())
        assertTrue(decodedRef.attributes is LinkedHashMap)
        assertTrue(decodedUnit.attributes is LinkedHashMap)
    }


    @Test
    fun missingRequiredKeyNamesTheKey() {
        val encoded = sourcedRef.asExecutionValue() as MapExecutionValue
        val missingId = MapExecutionValue(encoded.values - DataModelKeys.id)

        val error = assertFailsWith<IllegalArgumentException> {
            DataRef.ofExecutionValue(missingId)
        }

        assertTrue(error.message.orEmpty().contains(DataModelKeys.id))
    }


    @Test
    fun wrongNodeNamesTheKey() {
        val encoded = sourcedRef.asExecutionValue() as MapExecutionValue
        val wrongId = MapExecutionValue(
            encoded.values + (DataModelKeys.id to NullExecutionValue)
        )

        val error = assertFailsWith<IllegalArgumentException> {
            DataRef.ofExecutionValue(wrongId)
        }

        assertTrue(error.message.orEmpty().contains(DataModelKeys.id))
    }
}
