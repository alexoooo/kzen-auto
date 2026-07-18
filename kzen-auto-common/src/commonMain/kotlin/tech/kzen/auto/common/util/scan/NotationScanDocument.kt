package tech.kzen.auto.common.util.scan

import kotlinx.serialization.Serializable


// SER5: wire-only DTO for GET /scan -> ClientRestApi.scanNotation. One entry per document, keyed in the
// response map by the document's relative-file path; the client rebuilds NotationScan / DocumentScan from it
// (Digest.parse / ResourcePath.parse), exactly as it did off the former hand-walked Map<String, Any>.
//
// documentDigest / resources stay String / Map<String, String> (not Digest / typed keys) to match the prior
// wire byte-for-byte and add no serializer surface. `resources = null` default is LOAD-BEARING: stock Json has
// encodeDefaults=false, so a resource-less document omits `resources` entirely (the legacy Jackson map emitted
// `"resources": null`); the client reads an absent key and an explicit null identically, so the difference is
// harmless — wire-only, same-release. Pinned by NotationScanDocument tests in WireDtoSerializerTest.
@Serializable
data class NotationScanDocument(
    val documentDigest: String,
    val resources: Map<String, String>? = null
)
