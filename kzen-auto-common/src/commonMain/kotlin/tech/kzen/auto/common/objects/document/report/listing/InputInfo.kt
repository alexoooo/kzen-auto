package tech.kzen.auto.common.objects.document.report.listing

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.common.util.data.DataLocationInfo


data class InputInfo(
    val browseDir: DataLocation,
    val files: List<DataLocationInfo>
) {
    companion object {
        private const val browseDirKey = "dir"
        private const val filesKey = "files"


        fun fromCollection(map: Map<String, Any>): InputInfo {
            @Suppress("UNCHECKED_CAST")
            val selected = map[filesKey]!! as List<Map<String, String>>

            return InputInfo(
                DataLocation.of(map[browseDirKey]!! as String),
                selected.map {
                    DataLocationInfo.fromCollection(it)
                }
            )
        }
    }


    fun isEmpty(): Boolean {
        return files.isEmpty()
    }


    fun toCollection(): Map<String, Any> {
        return mapOf(
            browseDirKey to browseDir.asString(),
            filesKey to files.map { it.toCollection() },
        )
    }
}