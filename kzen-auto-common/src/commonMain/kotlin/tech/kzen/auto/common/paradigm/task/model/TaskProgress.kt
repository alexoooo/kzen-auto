package tech.kzen.auto.common.paradigm.task.model


data class TaskProgress(
    val value: Any
//    val remainingFiles: PersistentMap<String, TaskStepProgress>
) {
    companion object {
//        fun ofNotStarted(keys: List<String>): TaskProgress {
//            return TaskProgress(
//                keys.map { it to TaskStepProgress.empty }.toPersistentMap())
//        }
//
//
//        fun fromCollection(collection: Map<String, Map<String, String>>): TaskProgress {
//            return TaskProgress(collection
//                .mapValues { TaskStepProgress.fromCollection(it.value) }
//                .toPersistentMap())
//        }

        fun fromCollection(value: Any): TaskProgress {
            return TaskProgress(value)
        }
    }

//
//    fun start(key: String): TaskProgress {
//        return TaskProgress(
//            remainingFiles.put(key, remainingFiles[key]!!.copy(started = true)))
//    }
//
//
//    fun finish(key: String): TaskProgress {
//        return TaskProgress(
//            remainingFiles.put(key, remainingFiles[key]!!.copy(finished = true)))
//    }
//
//
//    fun update(key: String, message: String): TaskProgress {
//        return TaskProgress(
//            remainingFiles.put(key, remainingFiles[key]!!.copy(message = message)))
//    }
//
//
//    fun toCollection(): Map<String, Map<String, String>> {
//        return remainingFiles.mapValues { it.value.toCollection() }
//    }

    fun toCollection(): Any {
        return value
    }
}