package tech.kzen.auto.common.paradigm.reactive

import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentMap


data class TaskProgress(
    val remainingFiles: PersistentMap<String, String>
) {
    companion object {
        fun ofNotStarted(fileNames: List<String>): TaskProgress {
            return TaskProgress(
                fileNames.map { it to "Not started" }.toPersistentMap())
        }


        fun fromCollection(collection: Map<String, String>): TaskProgress {
            return TaskProgress(collection.toPersistentMap())
        }
    }


    fun update(fileName: String, progress: String): TaskProgress {
        return TaskProgress(
            remainingFiles.put(fileName, progress))
    }



    fun toCollection(): Map<String, String> {
        return remainingFiles
    }
}