package tech.kzen.auto.client.objects.document.common

import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation


external interface AttributeEditorProps: react.Props {
    var clientState: ClientState
    var objectLocation: ObjectLocation
    var attributeName: AttributeName
}