package tech.kzen.auto.client.objects.document.common.attribute

import react.Props
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface AttributeEditorProps: Props {
    var objectLocation: ObjectLocation
    var attributeName: AttributeName

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
}