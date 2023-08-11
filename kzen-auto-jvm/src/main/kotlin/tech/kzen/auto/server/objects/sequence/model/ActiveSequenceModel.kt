package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.lib.common.model.location.ObjectLocation


data class ActiveSequenceModel(
    val steps: MutableMap<ObjectLocation, ActiveStepModel> = mutableMapOf(),
    var next: ObjectLocation? = null
)