package tech.kzen.auto.common.paradigm.imperative.model.control

sealed class ControlState


object InitialControlState : ControlState()


class InternalEvaluationState(
        index: Int
) : ControlState()


class BranchEvaluationState(
        index: Int
) : ControlState()

