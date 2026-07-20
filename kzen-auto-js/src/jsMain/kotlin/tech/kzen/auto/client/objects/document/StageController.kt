package tech.kzen.auto.client.objects.document

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.createContext
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.service.logic.ControlError
import tech.kzen.auto.client.util.DefinitionErrors
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.DocumentArchetype
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.FontWeight
import web.cssom.Length
import web.cssom.LineStyle
import web.cssom.NamedColor
import web.cssom.em
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface StageControllerProps: Props {
    var documentControllers: List<DocumentController>
    var mirroredGraphStore: MirroredGraphStore
    var navigationGlobal: NavigationGlobal
    var clientLogicGlobal: ClientLogicGlobal
}


external interface StageControllerState: State {
    var structure: GraphStructure?
    var documentPath: DocumentPath?
    var transition: Boolean

    // Definition failures grouped by the document they belong to; the open document's entry (if any) is shown
    // as an in-context error panel above its body. Recomputed on every graph-store update.
    var definitionErrorsByDocument: Map<DocumentPath, List<DefinitionErrors.Line>>

    // The run control (start / step / stop / …) the server last refused, shown above the document it was
    // aimed at. Cleared by the next control action.
    var controlError: ControlError?
}


//---------------------------------------------------------------------------------------------------------------------
class StageController(
    props: StageControllerProps
):
    RPureComponent<StageControllerProps, StageControllerState>(props),
    LocalGraphStore.Observer,
    NavigationGlobal.Observer,
    ClientLogicGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    data class CoordinateContext(
        val stageTop: Length,
        val stageLeft: Length
    ) {
        companion object {
            val origin = CoordinateContext(0.px, 0.px)
        }
    }


    companion object {
        val StageContext = createContext(CoordinateContext.origin)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val documentControllers: List<DocumentController>,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val clientLogicGlobal: ClientLogicGlobal
    ): ReactWrapper<Props> {
        override fun ChildrenBuilder.child(block: Props.() -> Unit) {
            StageController::class.react {
                documentControllers = this@Wrapper.documentControllers
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                navigationGlobal = this@Wrapper.navigationGlobal
                clientLogicGlobal = this@Wrapper.clientLogicGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StageControllerState.init(props: StageControllerProps) {
        structure = null
        documentPath = null
        transition = false
        definitionErrorsByDocument = emptyMap()
        controlError = null
    }


    override fun componentDidUpdate(
        prevProps: StageControllerProps,
        prevState: StageControllerState,
        snapshot: Any
    ) {
        if (state.documentPath != prevState.documentPath &&
                state.documentPath != null &&
                prevState.documentPath != null
        ) {
            setState {
                transition = true
            }
        }

        if (state.transition) {
            setState {
                transition = false
            }
        }
    }


    override fun componentDidMount() {
        props.clientLogicGlobal.observe(this)

        async {
            props.mirroredGraphStore.observe(this)
            props.navigationGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
        props.navigationGlobal.unobserve(this)
        props.clientLogicGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        val nextDefinitionErrors = DefinitionErrors.all(graphDefinition).groupBy { it.location.documentPath }
        setState {
            structure = graphDefinition.graphStructure
            definitionErrorsByDocument = nextDefinitionErrors
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        val nextDefinitionErrors = DefinitionErrors.all(graphDefinitionAttempt).groupBy { it.location.documentPath }
        setState {
            structure = graphDefinitionAttempt.graphStructure
            definitionErrorsByDocument = nextDefinitionErrors
        }
    }


    // Only the control error is kept: the logic state is published on every status tick, so storing more (or
    // setting state unconditionally) would churn this component — and the document body under it — per poll.
    override fun onLogic(clientLogicState: ClientLogicState) {
        val nextControlError = clientLogicState.controlError
        if (nextControlError == state.controlError) {
            return
        }

        setState {
            controlError = nextControlError
        }
    }


    override fun handleNavigation(
        documentPath: DocumentPath?,
        parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun documentArchetypeName(): ObjectName? {
        val notation = state.structure?.graphNotation
            ?: return null

        val path = state.documentPath
            ?: return null

        return DocumentArchetype.archetypeName(notation, path)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (state.transition) {
            return
        }

        renderDefinitionErrors()
        renderControlError()

        val archetypeName = documentArchetypeName()

        if (archetypeName == null) {
            renderMissingDocument()
        }
        else {
            renderDocumentController(archetypeName)
        }
    }


    // In-context error panel rendered ABOVE the document body (not instead of it) — the editor still loads from
    // notation, so the user can fix the cause in place. A null [heading] collapses it to nothing.
    //
    // NB: this container `div` is ALWAYS emitted (left empty when there's no error) so the document body
    //     rendered after it keeps a STABLE child index. As a *conditional* sibling it would index-shift the
    //     body below it on every appearance/removal, and React — matching unkeyed siblings by position — would
    //     type-mismatch the body into the panel's vacated slot and REMOUNT the entire document controller
    //     (ScriptController / JobController / …). That tears down the controller's store and ALL per-document UI
    //     state (step expansion, scroll position, in-progress editor buffers) every time the document's error
    //     state toggles — e.g. the first time a RunStep's `instructions` is selected and its definition error
    //     clears. Keeping the slot present (empty `div` ⇒ zero footprint) holds the body in place across toggles.
    private fun ChildrenBuilder.errorPanel(heading: String?, detail: ChildrenBuilder.() -> Unit) {
        div {
            if (heading == null) {
                return@div
            }

            css {
                margin = 1.em
                padding = 0.5.em
                color = NamedColor.red
                borderWidth = 1.px
                borderStyle = LineStyle.solid
                borderColor = NamedColor.red
                borderRadius = 4.px
            }

            div {
                css {
                    fontWeight = FontWeight.bold
                }
                +heading
            }

            detail()
        }
    }


    // The open document's objects that failed to define.
    private fun ChildrenBuilder.renderDefinitionErrors() {
        val documentPath = state.documentPath
        val lines = documentPath?.let { state.definitionErrorsByDocument[it] }.orEmpty()

        errorPanel(
            "This document has a notation error and can't run until it's fixed"
                .takeIf { lines.isNotEmpty() }
        ) {
            for (line in lines) {
                div {
                    key = Key(line.location.asString())
                    css {
                        marginTop = 0.25.em
                    }
                    +"${line.location.asString()} — ${line.detail}"
                }
            }
        }
    }


    // The run control the server last refused. Scoped to the document it was aimed at (an unscoped error —
    // one raised with no active run — shows anywhere rather than nowhere).
    private fun ChildrenBuilder.renderControlError() {
        val controlError = state.controlError
            ?.takeIf { it.documentPath == null || it.documentPath == state.documentPath }

        errorPanel(controlError?.label) {
            val detail = controlError?.detail
                ?: return@errorPanel

            div {
                css {
                    marginTop = 0.25.em
                }
                +detail
            }
        }
    }


    private fun ChildrenBuilder.renderMissingDocument() {
        div {
            css {
                marginLeft = 2.em
                paddingTop = 2.em
            }

            val mainDocuments = state
                .structure
                ?.graphNotation
                ?.let { AutoConventions.mainDocuments(it) }
                ?.filter { path -> !path.folder }
                ?: listOf()

            div {
                css {
                    fontSize = 1.5.em
                }

                if (mainDocuments.isEmpty()) {
                    +"Please create a document from the sidebar (left)"
                }
                else {
                    +"Please select a document from the sidebar (left)"
                }
            }
        }
    }


    private fun ChildrenBuilder.renderDocumentController(
        archetypeName: ObjectName
    ) {
        val documentController = props.documentControllers
            .singleOrNull { archetypeName == it.archetypeLocation().objectPath.name }

        if (documentController == null) {
            +"Document: $archetypeName"
        }
        else {
            documentController.body().child(this) {}
        }
    }
}