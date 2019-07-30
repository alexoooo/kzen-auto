package tech.kzen.auto.common.paradigm.imperative.model.control

sealed class ControlTransition


object EvaluateControlTransition : ControlTransition()


class InternalExecutionTransition(
        index: Int
) : ControlTransition()


class BranchExecutionTransition(
        index: Int
) : ControlTransition()