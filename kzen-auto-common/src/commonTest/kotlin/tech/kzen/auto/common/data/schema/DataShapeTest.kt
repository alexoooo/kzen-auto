package tech.kzen.auto.common.data.schema

import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.model.DataModelKeys
import tech.kzen.lib.common.exec.BooleanExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.platform.ClassName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue


class DataShapeTest {
    @Test
    fun executionValuesRoundTrip() {
        val tabular: DataShape = DataShape.Tabular(HeaderListing.of(listOf("alpha", "beta")))
        val payload: DataShape = DataShape.Payload(TypeMetadata(
            ClassName("example.Payload"),
            listOf(TypeMetadata.string),
            true
        ))

        assertEquals(tabular, DataShape.ofExecutionValue(tabular.asExecutionValue()))
        assertEquals(payload, DataShape.ofExecutionValue(payload.asExecutionValue()))
    }


    @Test
    fun serializationUsesExplicitKind() {
        val tabular: DataShape = DataShape.Tabular(HeaderListing.of(listOf("alpha", "beta")))
        val payload: DataShape = DataShape.Payload(TypeMetadata.string)

        val tabularJson = Json.encodeToString(tabular)
        val payloadJson = Json.encodeToString(payload)

        assertEquals(
            """{"kind":"tabular","header":["0|alpha","0|beta"]}""",
            tabularJson
        )
        assertEquals(
            """{"kind":"payload","type":{"class":"kotlin.String","generics":[],"nullable":false}}""",
            payloadJson
        )
        assertEquals(tabular, Json.decodeFromString<DataShape>(tabularJson))
        assertEquals(payload, Json.decodeFromString<DataShape>(payloadJson))
    }


    @Test
    fun payloadExecutionValueRequiresEveryTypeMetadataKey() {
        val completeType = TypeMetadata(
            ClassName("example.Container"),
            listOf(TypeMetadata.string),
            true
        ).asExecutionValue() as MapExecutionValue

        for (key in listOf(
            DataModelKeys.className,
            DataModelKeys.generics,
            DataModelKeys.nullable
        )) {
            assertPayloadFailure(
                MapExecutionValue(completeType.values - key),
                key
            )
        }

        val wrongValues = listOf(
            DataModelKeys.className to NullExecutionValue,
            DataModelKeys.generics to TextExecutionValue("not-a-list"),
            DataModelKeys.nullable to TextExecutionValue("not-a-boolean")
        )
        for ((key, wrongValue) in wrongValues) {
            assertPayloadFailure(
                MapExecutionValue(completeType.values + (key to wrongValue)),
                key
            )
        }
    }


    @Test
    fun payloadExecutionValueValidatesGenericTypesRecursively() {
        val incompleteGeneric = MapExecutionValue(mapOf(
            DataModelKeys.className to TextExecutionValue("example.Element"),
            DataModelKeys.generics to ListExecutionValue(emptyList())
        ))
        val outerType = MapExecutionValue(mapOf(
            DataModelKeys.className to TextExecutionValue("example.Container"),
            DataModelKeys.generics to ListExecutionValue(listOf(incompleteGeneric)),
            DataModelKeys.nullable to BooleanExecutionValue.of(false)
        ))

        assertPayloadFailure(outerType, DataModelKeys.nullable)

        val wrongNullableGeneric = MapExecutionValue(
            incompleteGeneric.values + (
                DataModelKeys.nullable to TextExecutionValue("not-a-boolean"))
        )
        val outerWithWrongNullable = MapExecutionValue(
            outerType.values + (
                DataModelKeys.generics to ListExecutionValue(listOf(wrongNullableGeneric)))
        )
        assertPayloadFailure(outerWithWrongNullable, DataModelKeys.nullable)

        val wrongGeneric = MapExecutionValue(
            outerType.values + (
                DataModelKeys.generics to ListExecutionValue(listOf(TextExecutionValue("not-a-map"))))
        )
        assertPayloadFailure(wrongGeneric, "${DataModelKeys.generics}[0]")
    }


    private fun assertPayloadFailure(type: MapExecutionValue, expectedKey: String) {
        val value = MapExecutionValue(mapOf(
            DataModelKeys.kind to TextExecutionValue(DataModelKeys.payloadKind),
            DataModelKeys.type to type
        ))

        val error = assertFailsWith<IllegalArgumentException> {
            DataShape.ofExecutionValue(value)
        }
        assertTrue(error.message.orEmpty().contains(expectedKey))
    }
}
