package tech.kzen.auto.common.paradigm.task.api

/**
 * marker
 */
interface TaskRun {
    fun close(error: Boolean)
}