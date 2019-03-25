package tech.kzen.auto.client.objects.document

import react.RProps
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.common.objects.document.DocumentArchetype


interface DocumentController: ReactWrapper<RProps> {
    fun type(): DocumentArchetype
}