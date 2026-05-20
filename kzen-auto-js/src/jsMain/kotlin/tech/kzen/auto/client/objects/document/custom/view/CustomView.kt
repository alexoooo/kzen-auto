package tech.kzen.auto.client.objects.document.custom.view

import react.ChildrenBuilder
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.objects.document.custom.view.obj.CustomObject
import tech.kzen.auto.client.objects.document.custom.view.obj.CustomObjectDragDrop
import tech.kzen.auto.client.objects.document.custom.view.obj.CustomObjectInfo
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.service.notation.NotationConventions


//---------------------------------------------------------------------------------------------------------------------
external interface CustomViewProps: Props {
    var customState: CustomState
    var viewStore: CustomViewStore
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomViewComponentState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomView(
    props: CustomViewProps
):
    RPureComponent<CustomViewProps, CustomViewComponentState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val customState = props.customState
        val graphStructure = ClientContext.clientStateGlobal.current()?.graphStructure()
            ?: return

        val mainObjectLocation = ObjectLocation(customState.documentPath, NotationConventions.mainObjectPath)
        val exportsState = CustomViewExports.current(customState.serverNotation, graphStructure, mainObjectLocation)
        val viewState = customState.view

        for ((index, entry) in customState.serverNotation.notations.map.entries.withIndex()) {
            val objectPath: ObjectPath = entry.key
            if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                continue
            }

            val objectLocation = ObjectLocation(customState.documentPath, objectPath)
            val info = CustomObjectInfo.derive(objectLocation, graphStructure, exportsState.membership)

            CustomObject::class.react {
                this.objectLocation = objectLocation
                this.info = info
                this.viewStore = props.viewStore
                this.attributeEditorManager = props.attributeEditorManager

                this.indexInDocument = index
                this.dropMarker = CustomObjectDragDrop.dropMarkerFor(
                    viewState.dragSourceIndex, viewState.dragOverIndex, viewState.dropAfter, index)
            }
        }

        CustomCreate::class.react {
            this.customState = props.customState
            this.viewStore = props.viewStore
            this.prototypes = CustomConventions.listPrototypes(graphStructure.graphNotation)
        }
    }
}
