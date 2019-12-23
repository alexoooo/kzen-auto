package tech.kzen.auto.common.paradigm.imperative.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.control.ControlTransition
import tech.kzen.lib.common.util.Digest


data class VisualStepTransition(
        val executionState: ExecutionValue?,
        val executionResult: ExecutionResult?,
        val controlTransition: ControlTransition?,
        val executionModelDigest: Digest
)