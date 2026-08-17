package tech.kzen.auto.client.objects.ribbon

import emotion.react.css
import react.ChildrenBuilder
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.logic.ClientLogicGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.platform.decodeURIComponent
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import web.cssom.*
import web.dom.document
import web.html.HTMLMetaElement


//---------------------------------------------------------------------------------------------------------------------
external interface HeaderControllerProps: react.Props {
    var documentControllers: List<DocumentController>
    var headerModel: HeaderModel?
    var navigationGlobal: NavigationGlobal
    var restClient: ClientRestApi
    var clientStateGlobal: ClientStateGlobal
    var clientLogicGlobal: ClientLogicGlobal
    var logicValidationGlobal: LogicValidationGlobal
}


external interface HeaderControllerState: react.State {
    var documentPath: DocumentPath?
    var transition: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// HeaderModel is projected by ProjectController via HeaderModel.Builder, which preserves reference
// identity across attribute-only Notation mutations. That lets RPureComponent's default shallow
// SCU bail without any custom override here.
//
// NB: documentPath is observed locally rather than received as a prop. Synchronous prop delivery
// from the parent's first observer-fired setState would mount the document's CustomHeader before
// the sibling StageController had a chance to mount the matching CustomController — and
// CustomHeader.componentDidMount looks up the shared store from the DocumentBridge, which
// CustomController provides in its render(). Subscribing locally puts handleNavigation into the same
// async microtask batch as StageController's subscribe, so both children render in one React phase
// and the store is provided (render runs before any componentDidMount) before commit.
class HeaderController(
    props: HeaderControllerProps
):
    RPureComponent<HeaderControllerProps, HeaderControllerState>(props),
    NavigationGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val documentControllers: List<DocumentController>,
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val restClient: ClientRestApi,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val clientLogicGlobal: ClientLogicGlobal,
        @Service private val logicValidationGlobal: LogicValidationGlobal
    ): ReactWrapper<HeaderControllerProps> {
        override fun ChildrenBuilder.child(block: HeaderControllerProps.() -> Unit) {
            HeaderController::class.react {
                documentControllers = this@Wrapper.documentControllers
                navigationGlobal = this@Wrapper.navigationGlobal
                restClient = this@Wrapper.restClient
                clientStateGlobal = this@Wrapper.clientStateGlobal
                clientLogicGlobal = this@Wrapper.clientLogicGlobal
                logicValidationGlobal = this@Wrapper.logicValidationGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun HeaderControllerState.init(props: HeaderControllerProps) {
        documentPath = null
        transition = false
    }


    private var mounted = false


    override fun componentDidMount() {
        mounted = true
        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.navigationGlobal.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.navigationGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
        prevProps: HeaderControllerProps,
        prevState: HeaderControllerState,
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


    //-----------------------------------------------------------------------------------------------------------------
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
        val model = props.headerModel
            ?: return null

        val path = state.documentPath
            ?: return null

        return model.archetypeNameByDocument[path]
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                backgroundColor = NamedColor.white
                paddingRight = 1.75.em
                paddingBottom = 1.px
                paddingLeft = 1.75.em
                minHeight = 55.px
                // Contain the floated logo / run controls: the run controls now stack onto two rows, so
                // the right float is taller than the in-flow ribbon on documents with a short ribbon —
                // a BFC here keeps that float from spilling below the header onto the content beneath.
                display = Display.flowRoot
            }

            span {
                css {
                    float = Float.left
                    marginLeft = (-11).px
                    marginTop = 7.px
                    marginRight = 1.em
                }
                renderLogo()
            }

            div {
                css {
                    float = Float.right
                    // Lay the project title next to the run controls, both aligned to the top of the
                    // header (not vertically centred), with a little breathing room from the very top.
                    display = Display.flex
                    alignItems = AlignItems.flexStart
                    marginTop = 0.5.em
                }
                renderRightFloat()
            }

            if (!state.transition) {
                val archetypeName = documentArchetypeName()
                if (archetypeName != null) {
                    renderHeaderController(archetypeName)
                }
            }
        }
    }


    // Logo hover text: "Kzen (home)" plus the running server's version + build timestamp (from the
    //  kzen-build meta tag emitted by indexPage), so the live build is always identifiable. Falls back
    //  to just "Kzen (home)" when the meta is absent/blank (e.g. a pure-IDE dev run without the stamp).
    private fun logoTitle(): String {
        val build = (document.querySelector("meta[name=\"kzen-build\"]") as? HTMLMetaElement)
            ?.content
            ?.takeIf { it.isNotBlank() }
        return if (build == null) "Kzen (home)" else "Kzen (home)\n$build"
    }


    private fun ChildrenBuilder.renderLogo() {
        a {
            href = "/"

            img {
                src = "$staticResourcePath/logo.png"

                css {
                    height = 42.px
                }

                title = logoTitle()
            }
        }
    }


    private fun ChildrenBuilder.renderRightFloat() {
        renderTitle()
        renderRunNavigation()
        renderStorageManager()
    }


    private fun ChildrenBuilder.renderTitle() {
        val baseUrl = props.restClient.baseUrl
        val projectTitle =
            if (baseUrl.isEmpty()) {
                "Running in dev mode"
            }
            else {
                decodeURIComponent(baseUrl).substringAfter("/")
            }

        div {
            css {
                marginRight = 0.5.em
                fontSize = 1.5.em
                color = NamedColor.gray
                fontStyle = FontStyle.italic
                display = Display.inlineBlock
            }

            title = "Project name"

            +projectTitle
        }
    }


    private fun ChildrenBuilder.renderRunNavigation() {
        div {
            css {
                display = Display.inlineBlock
            }

            HeaderRunController::class.react {
                clientStateGlobal = props.clientStateGlobal
                clientLogicGlobal = props.clientLogicGlobal
                logicValidationGlobal = props.logicValidationGlobal
                navigationGlobal = props.navigationGlobal
            }
        }
    }


    private fun ChildrenBuilder.renderStorageManager() {
        div {
            css {
                // A vertical stack: the storage icon on top, the validity indicator tucked in the space beneath it
                // (which the two-row run cluster to its left already makes tall enough), so the indicator adds no
                // height and shifts nothing.
                display = Display.inlineFlex
                flexDirection = FlexDirection.column
                alignItems = AlignItems.center
                marginLeft = 0.5.em
            }

            StorageManagerController::class.react {
                restClient = props.restClient
            }

            ValidationStatusDisplay::class.react {
                clientStateGlobal = props.clientStateGlobal
                logicValidationGlobal = props.logicValidationGlobal
            }
        }
    }


    private fun ChildrenBuilder.renderHeaderController(
        archetypeName: ObjectName
    ) {
        val documentController = props.documentControllers
            .singleOrNull { archetypeName == it.archetypeLocation().objectPath.name }

        if (documentController == null) {
            +"Header: $archetypeName"
        }
        else {
            documentController.header().child(this) {}
        }
    }
}
