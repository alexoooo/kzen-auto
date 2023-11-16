package tech.kzen.auto.client.objects.document.graph.edit

import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation


external interface AttributeEditorPropsOld: react.Props {
    var clientState: ClientState
    var objectLocation: ObjectLocation
    var attributeName: AttributeName
}