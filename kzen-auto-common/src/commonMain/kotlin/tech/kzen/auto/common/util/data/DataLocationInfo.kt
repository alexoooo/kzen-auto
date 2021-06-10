package tech.kzen.auto.common.util.data

import kotlinx.datetime.Instant


data class DataLocationInfo(
    val path: DataLocation,
    val name: String,
    val size: Long,
    val modified: Instant,
    val directory: Boolean
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
        check(! name.endsWith("/") && ! name.endsWith("\\"))
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
