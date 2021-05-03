package tech.kzen.auto.client.objects.document

import react.RProps
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.locate.ObjectLocation


interface DocumentController: ReactWrapper<RProps> {
    fun archetypeLocation(): ObjectLocation
}