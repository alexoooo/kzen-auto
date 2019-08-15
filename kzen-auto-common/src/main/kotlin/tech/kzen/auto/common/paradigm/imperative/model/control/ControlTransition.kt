package tech.kzen.auto.common.paradigm.imperative.model.control

sealed class ControlTransition


object EvaluateControlTransition : ControlTransition()


data class InternalExecutionTransition(
        val index: Int
) : ControlTransition()


data class BranchExecutionTransition(
        val index: Int
) : ControlTransition()