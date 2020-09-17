package tech.kzen.auto.client.objects.document

import kotlinx.css.LinearDimension
import react.RProps
import react.createContext
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.locate.ObjectLocation


interface DocumentController: ReactWrapper<RProps> {
    fun archetypeLocation(): ObjectLocation
}