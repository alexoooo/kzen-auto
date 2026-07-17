package tech.kzen.auto.common.util.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant


// SER3 — DUAL-PLANE (2a Bucket C). Two encodings, deliberately:
//   * WIRE: the generated kotlinx codec (@Serializable below) — GET /file-listing -> ClientRestApi.listFiles.
//     `size` is a real JSON number and `dir` a real boolean here (were stringly). `modified` rides kotlinx's
//     built-in kotlin.time.Instant serializer, whose ISO-8601 string form is identical to toCollection()'s.
//   * VALUE-TREE: toCollection()/ofCollection() below are RETAINED for the ExecutionValue plane —
//     InputBrowserInfo.asCollection/ofCollection and InputDataInfo.asCollection/ofCollection embed this class's
//     map form inside a detached-action result. Those are still stringly.
// DO NOT delete the toCollection()/ofCollection() pair along with the storage DTOs' codecs: it looks dead once
// ClientRestApi's wire call site goes, but the four value-tree call sites above are live. The two encodings
// differ (map form is all-Strings) and never meet. If the value-tree plane is ever migrated, this pair goes then.
//
// NB: the init check below survives decoding ONLY because no property has a default — the plugin emits a
// synthetic bitmask constructor that bypasses init blocks as soon as one does. Do not add a default here
// without re-checking that. Pinned by dataLocationInfoInitCheckSurvivesDecoding in WireDtoSerializerTest.
@Serializable
data class DataLocationInfo(
    val path: DataLocation,
    val name: String,
    val size: Long,
    val modified: Instant,
    @SerialName("dir") val directory: Boolean
):
    Comparable<DataLocationInfo>
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val pathKey = "path"
        private const val nameKey = "name"
        private const val sizeKey = "size"
        private const val modifiedKey = "modified"
        private const val directoryKey = "dir"
        private const val missingSize = -1L
        private val missingModified = Instant.DISTANT_PAST


        fun ofMissingFile(path: DataLocation, name: String): DataLocationInfo {
            return DataLocationInfo(path, name, missingSize, missingModified, false)
        }


//        fun ofMissingDirectory(path: DataLocation, name: String): DataLocationInfo {
//            return DataLocationInfo(path, name, missingSize, missingModified, true)
//        }


        fun ofFile(path: DataLocation, name: String, size: Long, modified: Instant): DataLocationInfo {
            check(size >= 0)
            return DataLocationInfo(path, name, size, modified, false)
        }


        fun ofDirectory(path: DataLocation, name: String, modified: Instant): DataLocationInfo {
            return DataLocationInfo(path, name, 0, modified, true)
        }


        fun ofCollection(map: Map<String, String>): DataLocationInfo {
            return DataLocationInfo(
                DataLocation.of(map[pathKey]!!),
                map[nameKey]!!,
                map[sizeKey]!!.toLong(),
                Instant.parse(map[modifiedKey]!!),
                map[directoryKey]!!.toBoolean()
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        check(!name.endsWith("/") && !name.endsWith("\\"))
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun isMissing(): Boolean {
        return size == missingSize
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun toCollection(): Map<String, String> {
        return mapOf(
            pathKey to path.asString(),
            nameKey to name,
            sizeKey to size.toString(),
            modifiedKey to modified.toString(),
            directoryKey to directory.toString()
        )
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun compareTo(other: DataLocationInfo): Int {
        return path.asString().compareTo(other.path.asString())
    }
}
