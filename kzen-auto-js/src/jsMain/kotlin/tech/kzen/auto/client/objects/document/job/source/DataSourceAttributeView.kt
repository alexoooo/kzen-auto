package tech.kzen.auto.client.objects.document.job.source

import emotion.react.css
import js.objects.unsafeJso
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.StageObjectLocator
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeView
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*


external interface DataSourceAttributeViewProps : AttributeViewProps {
    var navigationGlobal: NavigationGlobal
}


external interface DataSourceAttributeViewState : State {
    var openDocumentPath: DocumentPath?
    var sourceLocation: ObjectLocation?
    var sourceType: String?
    var missingReference: String?
    var resolveState: DataSourceResolveStore.State?
}


class DataSourceAttributeView(
    props: DataSourceAttributeViewProps
) :
    ObjectScopedComponent<DataSourceAttributeViewProps, DataSourceAttributeViewState>(props),
    DataSourceResolveStore.Observer
{
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val navigationGlobal: NavigationGlobal
    ) : AttributeView(objectLocation) {
        override fun ChildrenBuilder.child(block: AttributeViewProps.() -> Unit) {
            DataSourceAttributeView::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                navigationGlobal = this@Wrapper.navigationGlobal
                block()
            }
        }
    }


    init {
        installContextType(DocumentBridgeContext)
    }


    private val objectLocator = StageObjectLocator(props.navigationGlobal)
    private var resolveStore: DataSourceResolveStore? = null
    private var observedSource: ObjectLocation? = null


    override fun componentDidMount() {
        resolveStore = contextValue<DocumentBridge?>()?.lookup(DataSourceResolveStoreKey)
        super.componentDidMount()
    }


    override fun componentWillUnmount() {
        observedSource?.let { resolveStore?.unobserve(it, this) }
        observedSource = null
        super.componentWillUnmount()
    }


    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation
        val rawReference = (graphNotation.firstAttribute(
            props.objectLocation,
            AttributePath.ofName(props.attributeName)
        ) as? ScalarAttributeNotation)?.value.orEmpty()
        val sourceLocation = rawReference
            .takeIf { it.isNotEmpty() }
            ?.let(ObjectReference::tryParse)
            ?.let { graphNotation.coalesce.locateOptional(
                it, ObjectReferenceHost.ofLocation(props.objectLocation)) }
        val sourceType = sourceLocation?.let { source ->
            graphNotation.inheritanceChain(source).drop(1).firstOrNull()?.objectPath?.name?.value
        }
        val missingReference = rawReference.takeIf { it.isNotEmpty() && sourceLocation == null }

        rebind(sourceLocation)
        if (state.openDocumentPath == clientState.navigationRoute.documentPath &&
                state.sourceLocation == sourceLocation &&
                state.sourceType == sourceType &&
                state.missingReference == missingReference
        ) {
            return
        }

        setState {
            openDocumentPath = clientState.navigationRoute.documentPath
            this.sourceLocation = sourceLocation
            this.sourceType = sourceType
            this.missingReference = missingReference
        }
    }


    private fun rebind(source: ObjectLocation?) {
        if (observedSource == source) {
            return
        }
        observedSource?.let { resolveStore?.unobserve(it, this) }
        observedSource = source
        if (source == null) {
            onDataSourceResolveState(null)
        }
        else {
            resolveStore?.observe(source, this)
        }
    }


    override fun onDataSourceResolveState(state: DataSourceResolveStore.State?) {
        if (this.state.resolveState == state) {
            return
        }
        setState {
            resolveState = state
        }
    }


    override fun ChildrenBuilder.render() {
        state.missingReference?.let { reference ->
            span {
                css {
                    fontSize = 0.85.em
                    color = Color("#c62828")
                }
                +"Source missing: $reference"
            }
            return
        }

        val source = state.sourceLocation
        if (source == null) {
            span {
                css {
                    fontSize = 0.85.em
                    color = Color("rgba(0, 0, 0, 0.55)")
                }
                +"Source not selected"
            }
            return
        }

        a {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                maxWidth = 100.pct
                color = Color("rgba(0, 0, 0, 0.6)")
                textDecoration = Globals.initial
                cursor = Cursor.pointer

                "&:hover" { color = Color("#1565ff") }
            }
            href = NavigationRoute(source.documentPath, RequestParams.empty).toFragment()
            onClick = { event ->
                event.preventDefault()
                event.stopPropagation()
                objectLocator.locate(source, state.openDocumentPath)
            }

            val type = state.sourceType ?: "Data source"
            +"$type \"${source.objectPath.name.value}\""
            teaser()?.let { +" · $it" }
            icon("material-symbols:open-in-new") {
                style = unsafeJso {
                    fontSize = 1.em
                    marginLeft = 0.25.em
                }
            }
        }
    }


    private fun teaser(): String? {
        val units = state.resolveState?.result?.manifest?.units
            ?: return null
        val count = "${units.size} ${if (units.size == 1) "unit" else "units"}"
        val firstName = units.firstOrNull()
            ?.parts
            ?.firstOrNull()
            ?.ref
            ?.let { ref -> ref.asLocationOrNull()?.fileName() ?: ref.display() }
        return if (firstName == null) count else "$count · $firstName"
    }
}
