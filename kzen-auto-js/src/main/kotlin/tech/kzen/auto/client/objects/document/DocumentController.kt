package tech.kzen.auto.client.objects.document

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.locate.ObjectLocation


interface DocumentController: ReactWrapper<react.Props> {
    fun archetypeLocation(): ObjectLocation
}