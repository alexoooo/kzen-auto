package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.custom.PrototypeConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomDocumentViewProps: Props {
    var documentPath: DocumentPath
    var clientState: ClientState
    var serverNotation: DocumentObjectNotation
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomDocumentViewState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomDocumentView(
    props: CustomDocumentViewProps
):
    RPureComponent<CustomDocumentViewProps, CustomDocumentViewState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val graphStructure = props.clientState.graphStructure()
        val graphMetadata = graphStructure.graphMetadata
        val graphNotation = graphStructure.graphNotation

        for ((objectPath, _) in props.serverNotation.notations.map) {
            if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                continue
            }

            val objectLocation = ObjectLocation(props.documentPath, objectPath)
            val objectMetadata = graphMetadata.objectMetadata[objectLocation]
            val isAbstract = graphNotation
                .directAttribute(objectLocation, NotationConventions.abstractAttributePath)
                ?.asBoolean()
                ?: false

            div {
                css {
                    marginBottom = 1.em
                }

                CustomDocumentObject::class.react {
                    this.objectPath = objectPath
                    this.objectLocation = objectLocation
                    this.objectMetadata = objectMetadata
                    this.isAbstract = isAbstract
                    this.attributeEditorManager = props.attributeEditorManager
                }
            }
        }

        CustomDocumentNew::class.react {
            this.documentPath = props.documentPath
            this.documentNotation = props.serverNotation
            this.prototypes = PrototypeConventions.listPrototypes(graphNotation)
        }
    }
}
