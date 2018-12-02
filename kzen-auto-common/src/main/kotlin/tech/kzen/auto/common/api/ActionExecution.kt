package tech.kzen.auto.common.api

import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.util.Digest


data class ActionExecution(
        val status: ExecutionStatus,
        val digest: Digest
)