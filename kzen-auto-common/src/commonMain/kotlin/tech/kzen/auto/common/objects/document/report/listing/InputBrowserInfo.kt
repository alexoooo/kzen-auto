package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo


data class InputBrowserInfo(
    val browseDir: DataLocation,
    val files: List<DataLocationInfo>
) {
    companion object {
        private const val browseDirKey = "dir"
        private const val filesKey = "files"


        fun ofCollection(map: Map<String, Any>): InputBrowserInfo {
            @Suppress("UNCHECKED_CAST")
            val selected = map[filesKey]!! as List<Map<String, String>>

            return InputBrowserInfo(
                DataLocation.of(map[browseDirKey]!! as String),
                selected.map {
                    DataLocationInfo.ofCollection(it)
                }
            )
        }
    }


    fun isEmpty(): Boolean {
        return files.isEmpty()
    }


    fun asCollection(): Map<String, Any> {
        return mapOf(
            browseDirKey to browseDir.asString(),
            filesKey to files.map { it.toCollection() },
        )
    }
}