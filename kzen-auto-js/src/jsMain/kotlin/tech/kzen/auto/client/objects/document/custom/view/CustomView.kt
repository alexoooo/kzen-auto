package tech.kzen.auto.client.objects.document.custom.view

import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.dragdrop.dropMarkerFor
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.objects.document.custom.view.obj.CustomObject
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore


//---------------------------------------------------------------------------------------------------------------------
external interface CustomViewProps: Props {
    var customState: CustomState
    var customViewModel: CustomViewModel?
    var viewStore: CustomViewStore
    var attributeEditorManager: AttributeEditorManager.Wrapper
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
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
        val customViewModel = props.customViewModel
            ?: return

        val viewState = props.customState.view

        for ((index, entry) in customViewModel.orderedEntries.withIndex()) {
            CustomObject::class.react {
                this.key = Key(entry.objectLocation.objectPath.asString())
                this.objectLocation = entry.objectLocation
                this.info = entry.info
                this.viewStore = props.viewStore
                this.attributeEditorManager = props.attributeEditorManager
                this.mirroredGraphStore = props.mirroredGraphStore

                this.indexInDocument = index
                this.dropMarker = dropMarkerFor(
                    viewState.dragSourceIndex, viewState.dragOverIndex, viewState.dropAfter, index)
            }
        }

        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return

        CustomCreate::class.react {
            this.customState = props.customState
            this.viewStore = props.viewStore
            this.prototypes = CustomConventions.listPrototypes(graphStructure.graphNotation)
        }
    }
}
