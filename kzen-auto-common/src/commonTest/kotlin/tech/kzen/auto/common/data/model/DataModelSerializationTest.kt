package tech.kzen.auto.common.data.model

import kotlinx.serialization.json.Json
import tech.kzen.auto.common.objects.document.plugin.model.CommonDataEncodingSpec
import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class DataModelSerializationTest {
    @Test
    fun manifestRoundTrips() {
        val manifest = DataManifest(listOf(
            DataUnit(
                linkedMapOf("date" to "2026-08-23", "region" to "north"),
                listOf(
                    DataPart(
                        DataRole.main,
                        DataRef(
                            null,
                            "/data/main.csv",
                            linkedMapOf("zeta" to "last", "alpha" to "first")
                        ),
                        CommonPluginCoordinate("csv"),
                        CommonDataEncodingSpec.ofString("UTF-8")
                    ),
                    DataPart(
                        DataRole("reference"),
                        DataRef(DataSourceId("provider-7"), "lookup/current"),
                        null,
                        null
                    )
                )
            ),
            DataUnit.ofPath("/data/second.csv")
        ))

        val encoded = Json.encodeToString(manifest)
        val decoded = Json.decodeFromString<DataManifest>(encoded)

        assertEquals(manifest, decoded)
        assertTrue(encoded.contains(
            "\"attributes\":{\"date\":\"2026-08-23\",\"region\":\"north\"}"))
        assertTrue(encoded.contains(
            "\"attributes\":{\"zeta\":\"last\",\"alpha\":\"first\"}"))
        assertEquals(listOf("date", "region"), decoded.units.first().attributes.keys.toList())
        assertTrue(decoded.units.first().attributes is LinkedHashMap)
        val decodedRefAttributes = decoded.units.first().parts.first().ref.attributes
        assertEquals(listOf("zeta", "alpha"), decodedRefAttributes.keys.toList())
        assertTrue(decodedRefAttributes is LinkedHashMap)
    }


    @Test
    fun valueClassesEncodeAsStrings() {
        assertEquals("\"main\"", Json.encodeToString(DataRole.main))
        assertEquals("\"provider-7\"", Json.encodeToString(DataSourceId("provider-7")))
    }
}
