package tech.kzen.auto.client.objects.document

import react.Props
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation


interface DocumentController {
    fun archetypeLocation(): ObjectLocation

    fun header(): ReactWrapper<Props>

    fun body(): ReactWrapper<Props>
}