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
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomViewProps: Props {
    var documentPath: DocumentPath
    var clientState: ClientState
    var serverNotation: DocumentObjectNotation
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomViewState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomView(
    props: CustomViewProps
):
    RPureComponent<CustomViewProps, CustomViewState>(props)
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

                CustomObject::class.react {
                    this.objectPath = objectPath
                    this.objectLocation = objectLocation
                    this.objectMetadata = objectMetadata
                    this.isAbstract = isAbstract
                    this.attributeEditorManager = props.attributeEditorManager
                }
            }
        }

        CustomNew::class.react {
            this.documentPath = props.documentPath
            this.documentNotation = props.serverNotation
            this.prototypes = CustomConventions.listPrototypes(graphNotation)
        }
    }
}
