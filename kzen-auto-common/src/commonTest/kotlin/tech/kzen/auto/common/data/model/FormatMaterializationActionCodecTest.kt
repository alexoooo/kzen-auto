package tech.kzen.auto.common.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.format.FormatMaterializationActionRequest
import tech.kzen.auto.common.data.format.FormatMaterializationActionResult
import tech.kzen.auto.common.data.format.FormatMaterializationIntent
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import kotlin.test.Test
import kotlin.test.assertEquals


class FormatMaterializationActionCodecTest {
    @Test
    fun requestJsonRoundTripsNullableOverrides() {
        val request = FormatMaterializationActionRequest(
            testDataPart(DataRole.main, DataRef(null, "orders.csv")),
            "formats.yaml#Csv",
            linkedMapOf("delimiter" to ";", "commentPrefix" to null),
            FormatMaterializationIntent.LockColumns)

        assertEquals(request, Json.decodeFromString<FormatMaterializationActionRequest>(
            Json.encodeToString(request)))
    }

    @Test
    fun absentIntentDecodesAsOverride() {
        val request = FormatMaterializationActionRequest(
            testDataPart(DataRole.main, DataRef(null, "orders.csv")),
            "formats.yaml#Csv",
            emptyMap())
        val encoded = Json.encodeToString(request)
            .replace(",\"intent\":\"override\"", "")

        assertEquals(
            FormatMaterializationIntent.Override,
            Json.decodeFromString<FormatMaterializationActionRequest>(encoded).intent)
    }


    @Test
    fun resultExecutionValueRoundTripsNotationAndOptionalMembers() {
        val result = FormatMaterializationActionResult(
            "main/job.yaml#input orders format",
            MapExecutionValue(mapOf(
                "is" to TextExecutionValue("formats.yaml#Csv"),
                "delimiter" to TextExecutionValue(";"))),
            null,
            null,
            null,
            "windows-1252")

        assertEquals(result, FormatMaterializationActionResult.ofExecutionValue(result.asExecutionValue()))
    }
}
