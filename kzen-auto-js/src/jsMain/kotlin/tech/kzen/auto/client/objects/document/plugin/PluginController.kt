package tech.kzen.auto.client.objects.document.plugin

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.plugin.PluginConventions
import tech.kzen.auto.common.objects.document.plugin.model.PluginClassDetail
import tech.kzen.auto.common.objects.document.plugin.model.PluginScopeDetail
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface PluginControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var restClient: ClientRestApi
    var mirroredGraphStore: MirroredGraphStore
}


external interface PluginControllerState: State {
    var clientState: ClientState?
    var scopes: List<PluginScopeDetail>?
    var listingError: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * The Plugin document: a read-only view of the installed plugin universe as the server pinned it at start —
 * one card per scope (the application classpath first), its discovered contributions, the classes this
 * workspace resolved with their availability here, and every named failure. Installation is a filesystem act
 * (a folder under the plugin root, applied at the next start); nothing on this page changes the server.
 */
@Suppress("unused")
class PluginController(
    props: PluginControllerProps
):
    RPureComponent<PluginControllerProps, PluginControllerState>(props),
    ClientStateGlobal.DocumentScopedObserver
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val loadedColor = NamedColor.darkgreen
        private val failedColor = NamedColor.darkred
        private val unavailableColor = NamedColor.darkorange
        private val mutedColor = NamedColor.gray

        fun tryMainLocation(clientState: ClientState): ObjectLocation? {
            val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

            val documentNotation = clientState
                .graphStructure()
                .graphNotation
                .documents[documentPath]
                ?: return null

            if (!PluginConventions.isPlugin(documentNotation)) {
                return null
            }

            return documentPath.toMainObjectLocation()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    PluginController::class.react {
                        clientStateGlobal = this@Wrapper.clientStateGlobal
                        restClient = this@Wrapper.restClient
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun PluginControllerState.init(props: PluginControllerProps) {
        clientState = null
        scopes = null
        listingError = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    override fun componentDidUpdate(prevProps: PluginControllerProps, prevState: PluginControllerState, snapshot: Any) {
        val clientState = state.clientState
            ?: return

        if (clientState.navigationRoute.documentPath != prevState.clientState?.navigationRoute?.documentPath) {
            loadInfo()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("MoveVariableDeclarationIntoWhen")
    private fun loadInfo() {
        val clientState = state.clientState
            ?: return

        val mainObjectLocation = tryMainLocation(clientState)
            ?: return

        async {
            val result = props.restClient.performDetached(mainObjectLocation)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val collections = result.value.get() as List<Map<String, Any?>>

                    val loaded = collections.map { PluginScopeDetail.ofCollection(it) }
                    setState {
                        scopes = loaded
                        listingError = null
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        scopes = null
                        listingError = result.errorMessage
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val clientState = state.clientState
            ?: return

        val mainObjectLocation = tryMainLocation(clientState)
            ?: return

        div {
            css {
                margin = Margin(5.em, 2.em, 2.em, 2.em)
            }

            renderIntro()
            renderListing()
        }
    }


    private fun ChildrenBuilder.renderIntro() {
        div {
            css {
                color = mutedColor
                marginBottom = 0.5.em
            }
            +"Installed plugins, as pinned when this server started. Install a plugin by placing its jars in a "
            span {
                css { fontFamily = FontFamily.monospace }
                +"plugins/<name>/"
            }
            +" folder (or the --plugin.root= directory) and restart. Only what a plugin contributed through an explicit "
            +"protocol is listed; a class appears once this workspace has resolved it."
        }
    }


    private fun ChildrenBuilder.renderListing() {
        val scopes = state.scopes
        val listingError = state.listingError

        div {
            css {
                marginTop = 0.5.em
            }

            when {
                scopes != null ->
                    renderScopes(scopes)

                listingError != null ->
                    +"Error: $listingError"

                else ->
                    +"Loading..."
            }
        }
    }


    private fun ChildrenBuilder.renderScopes(scopes: List<PluginScopeDetail>) {
        if (scopes.isEmpty()) {
            +"Empty"
            return
        }

        for (scope in scopes) {
            div {
                key = Key(scope.id)

                css {
                    filter = dropShadow(0.px, 1.px, 1.px, NamedColor.gray)
                    borderRadius = 3.px
                    backgroundColor = NamedColor.white
                    width = 100.pct.minus(2.em)
                    padding = Padding(1.em, 1.em, 1.em, 1.em)
                    marginTop = 1.em
                }

                renderScopeHeader(scope)
                renderScopeBody(scope)
            }
        }
    }


    private fun ChildrenBuilder.renderScopeHeader(scope: PluginScopeDetail) {
        h2 {
            css {
                marginTop = (-0.5).em
                marginBottom = 0.px
            }
            +scope.id
            span {
                css {
                    marginLeft = 1.em
                    fontSize = 0.6.em
                    fontWeight = FontWeight.normal
                    color = if (scope.loaded) loadedColor else failedColor
                }
                +(if (scope.loaded) "loaded" else "failed")
            }
        }

        div {
            css {
                color = mutedColor
                fontSize = 0.9.em
            }
            +(scope.directory ?: "application classpath")
            scope.version?.let { +" · version $it" }
            scope.spiVersion?.let { +" · SPI $it" }
            if (scope.jars.isNotEmpty()) {
                +" · "
                span {
                    css { fontFamily = FontFamily.monospace }
                    +scope.jars.joinToString()
                }
            }
        }
    }


    private fun ChildrenBuilder.renderScopeBody(scope: PluginScopeDetail) {
        scope.failure?.let { renderLine("Failed to load", it, failedColor) }

        for (failure in scope.failures) {
            renderLine("Failure", failure, failedColor)
        }

        for (reader in scope.readers) {
            renderLine("Reader", reader)
        }

        if (scope.isApplication) {
            // kzen's own bundled notation is not what this page is for; one line keeps the row honest
            if (scope.documents.isNotEmpty()) {
                renderLine("Bundled documents", scope.documents.size.toString(), detail = "kzen's own notation")
            }
        }
        else {
            for (document in scope.documents) {
                renderLine("Document", document.path, detail = document.origin)
            }
        }

        for (module in scope.generatedModules) {
            renderLine("Generated module", module)
        }

        for (klass in scope.classes) {
            val color = when (klass.availability) {
                PluginClassDetail.available -> loadedColor
                PluginClassDetail.unavailable -> unavailableColor
                else -> failedColor
            }
            val caption = when (klass.availability) {
                PluginClassDetail.available -> "available in this workspace"
                PluginClassDetail.unavailable -> "unavailable in this workspace — " + (klass.detail ?: "")
                else -> "cannot be served — " + (klass.detail ?: "")
            }
            renderLine("Class", klass.className, color, caption)
        }

        for (name in scope.shadowedClasses) {
            renderLine("Shadowed", name, unavailableColor, "the application classpath defines it; the application copy is used")
        }

        for (name in scope.ambiguousClasses) {
            renderLine("Ambiguous", name, failedColor, "defined by more than one plugin; resolution fails by name")
        }

        val nothingListed = scope.loaded && scope.failures.isEmpty() && scope.readers.isEmpty() &&
                scope.documents.isEmpty() && scope.generatedModules.isEmpty() && scope.classes.isEmpty() &&
                scope.shadowedClasses.isEmpty() && scope.ambiguousClasses.isEmpty()
        if (nothingListed) {
            div {
                css {
                    color = mutedColor
                    marginTop = 0.5.em
                }
                +"No contributions discovered and no class resolved yet."
            }
        }
    }


    private fun ChildrenBuilder.renderLine(
        label: String,
        value: String,
        valueColor: Color? = null,
        detail: String? = null
    ) {
        div {
            css {
                marginTop = 0.25.em
            }
            +"$label: "
            span {
                css {
                    fontFamily = FontFamily.monospace
                    valueColor?.let { color = it }
                }
                +value
            }
            detail?.let {
                span {
                    css {
                        marginLeft = 0.5.em
                        color = mutedColor
                        fontSize = 0.9.em
                    }
                    +it
                }
            }
        }
    }
}
