package tech.kzen.auto.client.objects.document.common.attribute

import react.Props
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation


external interface AttributeEditor2Props: Props {
    var objectLocation: ObjectLocation
    var attributeName: AttributeName
}