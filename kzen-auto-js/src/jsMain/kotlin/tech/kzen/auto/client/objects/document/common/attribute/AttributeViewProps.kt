package tech.kzen.auto.client.objects.document.common.attribute

import react.Props
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation


external interface AttributeViewProps: Props {
    var objectLocation: ObjectLocation
    var attributeName: AttributeName

    var clientStateGlobal: ClientStateGlobal
}
