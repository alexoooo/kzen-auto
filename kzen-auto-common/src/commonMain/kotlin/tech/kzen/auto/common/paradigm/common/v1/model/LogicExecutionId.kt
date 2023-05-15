package tech.kzen.auto.common.paradigm.common.v1.model


/**
 * Same logic can be executed multiple times within each run
 */
data class LogicExecutionId(
    val value: String
) {
    override fun toString(): String {
        return value
    }
}