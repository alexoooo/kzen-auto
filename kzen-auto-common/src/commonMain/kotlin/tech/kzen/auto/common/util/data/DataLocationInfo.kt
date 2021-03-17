package tech.kzen.auto.common.util.data

import kotlinx.datetime.Instant


data class DataLocationInfo(
    val path: DataLocation,
    val name: String,
    val size: Long,
    val modified: Instant,
    val directory: Boolean
) {
    companion object {
        private const val pathKey = "path"
        private const val nameKey = "name"
        private const val sizeKey = "size"
        private const val modifiedKey = "modified"
        private const val directoryKey = "dir"


        fun ofFile(path: DataLocation, name: String, size: Long, modified: Instant): DataLocationInfo {
            check(! name.endsWith("/"))
            return DataLocationInfo(path, name, size, modified, false)
        }


        fun ofDirectory(path: DataLocation, name: String, modified: Instant): DataLocationInfo {
            check(! name.endsWith("/"))
            return DataLocationInfo(path, name, 0, modified, true)
        }


        fun fromCollection(map: Map<String, String>): DataLocationInfo {
            return DataLocationInfo(
                DataLocation.of(map[pathKey]!!),
                map[nameKey]!!,
                map[sizeKey]!!.toLong(),
                Instant.parse(map[modifiedKey]!!),
                map[directoryKey]!!.toBoolean()
            )
        }
    }


    fun toCollection(): Map<String, String> {
        return mapOf(
            pathKey to path.asString(),
            nameKey to name,
            sizeKey to size.toString(),
            modifiedKey to modified.toString(),
            directoryKey to directory.toString()
        )
    }
}
