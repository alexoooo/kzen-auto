package tech.kzen.auto.client.objects.document.common

import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation


external interface AttributeEditorProps: react.Props {
    var clientState: SessionState
    var objectLocation: ObjectLocation
    var attributeName: AttributeName
}