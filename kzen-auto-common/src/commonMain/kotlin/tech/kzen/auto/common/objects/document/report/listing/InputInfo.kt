package tech.kzen.auto.common.objects.document.report.listing


data class InputInfo(
    val browseDir: String,
    val files: List<FileInfo>
) {
    companion object {
        private const val browseDirKey = "dir"
        private const val filesKey = "files"


        fun fromCollection(map: Map<String, Any>): InputInfo {
            @Suppress("UNCHECKED_CAST")
            val selected = map[filesKey]!! as List<Map<String, String>>

            return InputInfo(
                map[browseDirKey]!! as String,
                selected.map {
                    FileInfo.fromCollection(it)
                }
            )
        }
    }


    fun isEmpty(): Boolean {
        return files.isEmpty()
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            browseDirKey to browseDir,
            filesKey to files.map { it.toCollection() },
        )
    }
}