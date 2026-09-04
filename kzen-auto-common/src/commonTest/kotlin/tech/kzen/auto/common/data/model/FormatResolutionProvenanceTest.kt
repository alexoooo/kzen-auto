package tech.kzen.auto.common.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import tech.kzen.auto.common.data.format.FormatResolutionBasis
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatSelectionKind
import kotlin.test.Test
import kotlin.test.assertEquals


class FormatResolutionProvenanceTest {
    @Test
    fun allProvenanceVariantsRoundTripWithOptionalWarningAndEncoding() {
        val ref = DataRef(null, "orders.csv")
        val details = FormatSelectionKind.entries.flatMap { selection ->
            FormatResolutionBasis.entries.mapIndexed { index, basis ->
                FormatResolutionDetail(
                    ref,
                    "formats.yaml#Csv",
                    "CSV",
                    selection,
                    basis,
                    "Resolution reason for ${basis.wireValue}",
                    if (index % 2 == 0) "Visible warning" else null,
                    DataRole("input-$index"),
                    if (index % 2 == 0) "UTF-8" else null,
                    columnsLocked = index % 2 == 0)
            }
        }
        val result = DataResolveResult(
            DataManifest(listOf(testDataUnit(ref.id))),
            emptyList(),
            details)

        assertEquals(result, DataResolveResult.ofExecutionValue(result.asExecutionValue()))
        assertEquals(result, Json.decodeFromString<DataResolveResult>(Json.encodeToString(result)))
    }


    @Test
    fun resolutionDetailsDoNotAffectDataPartDigest() {
        val part = testDataPart(DataRole.main, DataRef(null, "orders.csv"))
        val digestBefore = part.digest()
        val manifestDigestBefore = DataManifest(listOf(DataUnit.of(part))).digest()
        val details = listOf(FormatResolutionDetail(
            part.ref,
            "formats.yaml#Csv",
            "CSV",
            FormatSelectionKind.Automatic,
            FormatResolutionBasis.Content,
            "Comma-delimited records were detected",
            resolvedEncoding = "UTF-8"))

        DataResolveResult(DataManifest(listOf(DataUnit.of(part))), emptyList(), details)

        assertEquals(digestBefore, part.digest())
        assertEquals(manifestDigestBefore, DataManifest(listOf(DataUnit.of(part))).digest())
    }
}
