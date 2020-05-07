package tech.kzen.auto.client.objects.document.common

import react.RProps
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation


open class AttributeEditorProps(
        var clientState: SessionState,
        var objectLocation: ObjectLocation,
        var attributeName: AttributeName
): RProps