package tech.kzen.auto.common.objects.document.report.listing

import kotlinx.datetime.Instant


data class FileInfo(
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


        fun ofFile(path: DataLocation, name: String, size: Long, modified: Instant): FileInfo {
            check(! name.endsWith("/"))
            return FileInfo(path, name, size, modified, false)
        }


        fun ofDirectory(path: DataLocation, name: String, modified: Instant): FileInfo {
            check(! name.endsWith("/"))
            return FileInfo(path, name, 0, modified, true)
        }


        fun fromCollection(map: Map<String, String>): FileInfo {
            return FileInfo(
                DataLocation.of(map[pathKey]!!),
                map[nameKey]!!,
                map[sizeKey]!!.toLong(),
                Instant.parse(map[modifiedKey]!!),
                map[directoryKey]!!.toBoolean()
            )
        }


        fun split(path: DataLocation): List<Pair<String, String>> {
            val parts = path.asString().split(Regex("[/\\\\]"))

            var cumulative = ""
            val builder = mutableListOf<Pair<String, String>>()
            var first = true

            for (part in parts) {
                cumulative =
                    if (first) {
                        first = false
                        part
                    }
                    else {
                        cumulative + path.asString().substring(cumulative.length, cumulative.length + 1) + part
                    }

                if (part.isNotEmpty()) {
                    builder.add(cumulative to part)
                }
            }

            return builder
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


//    fun isDirectory(): Boolean {
//        return name.endsWith("/")
//    }
}
