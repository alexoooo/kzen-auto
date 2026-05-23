package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.custom.model.CustomGlobal
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.objects.document.custom.model.CustomStore
import tech.kzen.auto.client.objects.document.custom.model.CustomViewMode
import tech.kzen.auto.client.objects.document.custom.raw.CustomRaw
import tech.kzen.auto.client.objects.document.custom.view.CustomView
import tech.kzen.auto.client.objects.document.custom.view.CustomViewModel
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.Margin
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomControllerProps: Props {
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomControllerState: State {
    var customState: CustomState?
    var customViewModel: CustomViewModel?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomController(
    props: CustomControllerProps
):
    RPureComponent<CustomControllerProps, CustomControllerState>(props),
    CustomStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        private val attributeEditorManager: AttributeEditorManager.Wrapper
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    CustomHeader::class.react {
                        block()
                    }
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    CustomController::class.react {
                        this.attributeEditorManager = this@Wrapper.attributeEditorManager
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = CustomStore().also { CustomGlobal.upsertWeak(it) }
    private val viewModelBuilder = CustomViewModel.Builder()


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomControllerState.init(props: CustomControllerProps) {
        customState = null
        customViewModel = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        store.observe(this)
        store.didMount()
    }


    override fun componentWillUnmount() {
        store.unobserve(this)
        store.willUnmount()
    }


    override fun onCustomState(customState: CustomState) {
        val graphStructure = ClientContext.clientStateGlobal.current()?.graphStructure()
        val nextViewModel = graphStructure?.let {
            viewModelBuilder.update(customState.documentPath, customState.serverNotation, it)
        }
        setState {
            this.customState = customState
            this.customViewModel = nextViewModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val customState = state.customState
            ?: return

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            when (customState.viewMode) {
                CustomViewMode.Raw ->
                    CustomRaw::class.react {
                        rawStore = store.raw
                        rawState = customState.raw
                        editorModified = customState.editorModified
                    }

                CustomViewMode.View ->
                    CustomView::class.react {
                        this.customState = customState
                        this.customViewModel = state.customViewModel
                        this.viewStore = store.view
                        this.attributeEditorManager = props.attributeEditorManager
                    }
            }
        }
    }
}
