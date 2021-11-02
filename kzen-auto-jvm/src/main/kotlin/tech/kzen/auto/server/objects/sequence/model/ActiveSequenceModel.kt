package tech.kzen.auto.server.objects.sequence.model

import tech.kzen.lib.common.model.locate.ObjectLocation


data class ActiveSequenceModel(
    val steps: MutableMap<ObjectLocation, ActiveStepModel>
)