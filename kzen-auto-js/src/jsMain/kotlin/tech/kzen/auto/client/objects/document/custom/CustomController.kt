package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.objects.document.common.raw.DocumentRaw
import tech.kzen.auto.client.objects.document.common.raw.DocumentViewMode
import tech.kzen.auto.client.objects.document.custom.model.CustomState
import tech.kzen.auto.client.objects.document.custom.model.CustomStore
import tech.kzen.auto.client.objects.document.custom.model.CustomStoreKey
import tech.kzen.auto.client.objects.document.custom.view.CustomView
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.service.rest.ClientRestTaskRepository
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.parse.NotationParser
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.Margin
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomControllerProps: Props {
    var attributeEditorManager: AttributeEditorManager.Wrapper

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
    var notationParser: NotationParser
    var restClient: ClientRestApi
    var clientRestTaskRepository: ClientRestTaskRepository
}


external interface CustomControllerState: State {
    var customState: CustomState?
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
        private val attributeEditorManager: AttributeEditorManager.Wrapper,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val notationParser: NotationParser,
        @Service private val restClient: ClientRestApi,
        @Service private val clientRestTaskRepository: ClientRestTaskRepository
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
                        this.clientStateGlobal = this@Wrapper.clientStateGlobal
                        this.mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        this.notationParser = this@Wrapper.notationParser
                        this.restClient = this@Wrapper.restClient
                        this.clientRestTaskRepository = this@Wrapper.clientRestTaskRepository
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    init {
        // Single per-document context; CustomController reads it in render to provide its store.
        installContextType(DocumentBridgeContext)
    }


    //-----------------------------------------------------------------------------------------------------------------
    // CustomHeader is mounted in a sibling slot and picks up this store from the DocumentBridge (CustomStoreKey,
    // provided in render below) — header and body share one store.
    private val store = CustomStore(
        props.clientStateGlobal,
        props.mirroredGraphStore,
        props.notationParser,
        props.restClient,
        props.clientRestTaskRepository
    )


    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomControllerState.init(props: CustomControllerProps) {
        customState = null
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
        setState {
            this.customState = customState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        // Provide the store into the bridge BEFORE the early return, so the sibling CustomHeader (header slot)
        // resolves it in its componentDidMount (which runs after this render)
        contextValue<DocumentBridge?>()?.provide(CustomStoreKey, store)

        val customState = state.customState
            ?: return

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            when (customState.viewMode) {
                DocumentViewMode.Raw ->
                    DocumentRaw::class.react {
                        rawStore = store.raw
                        rawState = customState.raw
                        editorModified = customState.editorModified
                    }

                DocumentViewMode.View ->
                    CustomView::class.react {
                        this.customState = customState
                        this.viewStore = store.view
                        this.attributeEditorManager = props.attributeEditorManager
                        this.mirroredGraphStore = props.mirroredGraphStore
                    }
            }
        }
    }
}
