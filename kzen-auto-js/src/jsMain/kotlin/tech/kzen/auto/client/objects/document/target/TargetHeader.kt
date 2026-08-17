package tech.kzen.auto.client.objects.document.target

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TargetHeaderProps: Props {
    var mirroredGraphStore: MirroredGraphStore
    var navigationGlobal: NavigationGlobal
}


external interface TargetHeaderState: State {
    var documentPath: DocumentPath?
    var parameters: RequestParams?
    var graphStructure: GraphStructure?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * View/Add tabs in the document header: anchors on the `section` hash param, so the selection
 * survives a refresh and each tab can be opened in a new browser tab.
 */
@Suppress("unused")
class TargetHeader(
    props: TargetHeaderProps
):
    RPureComponent<TargetHeaderProps, TargetHeaderState>(props),
    NavigationGlobal.Observer,
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun TargetHeaderState.init(props: TargetHeaderProps) {
        documentPath = null
        parameters = null
        graphStructure = null
    }


    private var mounted = false


    override fun componentDidMount() {
        mounted = true
        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.mirroredGraphStore.observe(this)
                props.navigationGlobal.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
        props.navigationGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(
        documentPath: DocumentPath?,
        parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
            this.parameters = parameters
        }
    }


    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        setState {
            this.graphStructure = graphDefinition.graphStructure
        }
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinitionAttempt.graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentPath = state.documentPath
            ?: return
        val documentNotation = state.graphStructure?.graphNotation?.documents?.get(documentPath)
            ?: return

        val parameters = state.parameters ?: RequestParams.empty
        val activeSection = TargetSection.active(
            parameters, TargetDocument.hasCrops(documentNotation))

        div {
            css {
                padding = Padding(0.5.em, 1.em)
            }

            renderTab(documentPath, parameters, activeSection, TargetSection.view, "View", first = true)
            renderTab(documentPath, parameters, activeSection, TargetSection.add, "Add", first = false)
        }
    }


    private fun ChildrenBuilder.renderTab(
        documentPath: DocumentPath,
        parameters: RequestParams,
        activeSection: String,
        section: String,
        label: String,
        first: Boolean
    ) {
        val active = section == activeSection

        a {
            css {
                // Match the MUI ToggleButtonGroup chrome used by other document headers
                display = Display.inlineFlex
                alignItems = AlignItems.center
                height = 34.px
                padding = Padding(0.px, 11.px)
                border = Border(2.px, LineStyle.solid, Color("#0000001f"))
                color = NamedColor.black
                textDecoration = None.none
                textTransform = TextTransform.uppercase
                fontSize = 0.875.rem

                if (first) {
                    borderTopLeftRadius = 4.px
                    borderBottomLeftRadius = 4.px
                }
                else {
                    // Collapse the shared border between adjacent tabs
                    marginLeft = (-2).px
                    borderTopRightRadius = 4.px
                    borderBottomRightRadius = 4.px
                }

                if (active) {
                    backgroundColor = Color("#00000014")
                }
                else {
                    hover {
                        backgroundColor = Color("#0000000a")
                    }
                }
            }

            draggable = false
            href = NavigationRoute(
                documentPath,
                parameters.set(TargetSection.parameterKey, section)
            ).toFragment()

            +label
        }
    }
}
