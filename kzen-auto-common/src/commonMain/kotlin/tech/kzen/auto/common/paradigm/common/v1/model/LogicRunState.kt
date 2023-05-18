package tech.kzen.auto.common.paradigm.common.v1.model


enum class LogicRunState {
    Running,
    Stepping,

    Pausing,
    Paused,

    Cancelling;


    fun isExecuting(): Boolean {
        return this == Running ||
                this == Stepping
    }
}