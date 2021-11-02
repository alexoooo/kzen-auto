package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class ActiveStepModel(
    var value: Any?,
    var detail: ExecutionValue?,
    var error: String?
)