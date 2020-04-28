package tech.kzen.auto.client.objects.document.common

import react.RProps
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


open class AttributeEditorProps(
//        var graphStructure: GraphStructure,
        var clientState: SessionState,
        var objectLocation: ObjectLocation,
        var attributeName: AttributeName
): RProps