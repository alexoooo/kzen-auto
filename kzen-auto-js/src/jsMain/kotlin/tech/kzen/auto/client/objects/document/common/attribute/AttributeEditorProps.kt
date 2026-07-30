package tech.kzen.auto.client.objects.document.common.attribute

import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedProps
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.service.store.MirroredGraphStore


external interface AttributeEditorProps: ObjectScopedProps {
    var attributeName: AttributeName

    var mirroredGraphStore: MirroredGraphStore
}
