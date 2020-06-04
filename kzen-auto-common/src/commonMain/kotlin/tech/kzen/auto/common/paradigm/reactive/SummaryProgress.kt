package tech.kzen.auto.common.paradigm.reactive

import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class SummaryProgress(
    val remainingFiles: PersistentMap<String, String>
) {
    companion object {
        fun ofNotStarted(fileNames: List<String>): SummaryProgress {
            return SummaryProgress(
                fileNames.map { it to "Not started" }.toPersistentMap())
        }


        fun fromCollection(collection: Map<String, String>): SummaryProgress {
            return SummaryProgress(collection.toPersistentMap())
        }
    }


    fun update(fileName: String, progress: String): SummaryProgress {
        return SummaryProgress(
            remainingFiles.put(fileName, progress))
    }



    fun toCollection(): Map<String, String> {
        return remainingFiles
    }
}