package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class ActiveStepModel(
    var value: Any? = null,
    var detail: ExecutionValue? = null,
    var error: String? = null
)