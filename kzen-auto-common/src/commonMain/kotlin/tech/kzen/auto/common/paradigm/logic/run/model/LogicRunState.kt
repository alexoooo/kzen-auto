package tech.kzen.auto.common.paradigm.logic.run.model


enum class LogicRunState {
    Running,
    Stepping,

    Pausing,
    Paused,

    Cancelling;


    fun isExecuting(): Boolean {
        return this != Paused
    }
}