package tech.kzen.auto.common.objects.document.report.listing

import kotlinx.datetime.Instant


data class FileInfo(
    val path: String,
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


        fun ofFile(path: String, name: String, size: Long, modified: Instant): FileInfo {
            check(! name.endsWith("/"))
            return FileInfo(path, name, size, modified, false)
        }


        fun ofDirectory(path: String, name: String, modified: Instant): FileInfo {
            check(! name.endsWith("/"))
            return FileInfo(path, name, 0, modified, true)
        }


        fun fromCollection(map: Map<String, String>): FileInfo {
            return FileInfo(
                map[pathKey]!!,
                map[nameKey]!!,
                map[sizeKey]!!.toLong(),
                Instant.parse(map[modifiedKey]!!),
                map[directoryKey]!!.toBoolean()
            )
        }
    }


    fun toCollection(): Map<String, String> {
        return mapOf(
            pathKey to path,
            nameKey to name,
            sizeKey to size.toString(),
            modifiedKey to modified.toString(),
            directoryKey to directory.toString()
        )
    }


//    fun isDirectory(): Boolean {
//        return name.endsWith("/")
//    }
}
