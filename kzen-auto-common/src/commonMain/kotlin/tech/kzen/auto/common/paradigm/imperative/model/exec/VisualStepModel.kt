package tech.kzen.auto.common.paradigm.imperative.model.exec

import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class VisualStepModel(
        /**
         * If true, then the below values, which refer to the "current" time, are potentially stale.
         */
        val running: Boolean,

        /**
         * Null means stateless
         */
        val state: ExecutionValue?,

        /**
         * Null means it hasn't run yet
         */
        val result: ExecutionResult?
)