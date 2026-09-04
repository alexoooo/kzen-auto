package tech.kzen.auto.common.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind


class DataModelSerializationTest {
    @Test
    fun manifestRoundTrips() {
        val manifest = DataManifest(listOf(
            DataUnit(
                linkedMapOf("date" to "2026-08-23", "region" to "north"),
                listOf(
                    testDataPart(
                        DataRole.main,
                        DataRef(
                            null,
                            "/data/main.csv",
                            linkedMapOf("zeta" to "last", "alpha" to "first")
                        )),
                    testDataPart(
                        DataRole("reference"),
                        DataRef(DataSourceId("provider-7"), "lookup/current"))
                )
            ),
            testDataUnit("/data/second.csv")
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


    @Test
    fun resolutionDetailsUseStableWireNamesAndRemainLegacyOptional() {
        val ref = DataRef(null, "/data/input.csv")
        val result = DataResolveResult(
            DataManifest(listOf(testDataUnit(ref.id))),
            emptyList(),
            listOf(FormatResolutionDetail(
                ref,
                "formats.yaml#Csv",
                "CSV",
                FormatSelectionKind.Automatic,
                FormatResolutionBasis.Content,
                "Comma-delimited records were detected")))

        val encoded = Json.encodeToString(result)

        assertTrue(encoded.contains("\"selection\":\"automatic\""), encoded)
        assertTrue(encoded.contains("\"basis\":\"content\""), encoded)
        assertEquals(result, Json.decodeFromString<DataResolveResult>(encoded))

        val legacy = Json.decodeFromString<DataResolveResult>(
            Json.encodeToString(result.copy(resolutionDetails = emptyList()))
                .replace(",\"resolutionDetails\":[]", ""))
        assertEquals(emptyList(), legacy.resolutionDetails)
    }
}
