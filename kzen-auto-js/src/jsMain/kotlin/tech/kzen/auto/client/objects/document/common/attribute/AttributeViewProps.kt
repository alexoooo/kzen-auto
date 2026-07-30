package tech.kzen.auto.client.objects.document.common.attribute

import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedProps
import tech.kzen.lib.common.model.attribute.AttributeName


external interface AttributeViewProps: ObjectScopedProps {
    var attributeName: AttributeName
}
