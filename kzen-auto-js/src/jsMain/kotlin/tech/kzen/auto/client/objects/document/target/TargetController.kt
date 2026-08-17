package tech.kzen.auto.client.objects.document.target

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.select
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.script.display.image.pngUrl
import tech.kzen.auto.client.objects.document.target.model.TargetState
import tech.kzen.auto.client.objects.document.target.model.TargetStore
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.target.model.TargetFetchPhase
import tech.kzen.auto.common.objects.document.target.model.TargetScreenshotSource
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface TargetControllerProps: Props {
    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore
    var navigationGlobal: NavigationGlobal
    var restClient: ClientRestApi
}


external interface TargetControllerState: State {
    var targetState: TargetState?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Two routed sub-pages: View (how do the captured patches match, live) and Add (capture a new
 * patch), selected by the `section` hash param via the [TargetHeader] tabs. The screenshot can
 * come from the desktop (with an optional delay to alt-tab) or from a run's browser trace
 * (bit-identical to what matching saw).
 */
@Suppress("unused")
class TargetController(
    props: TargetControllerProps
):
    RPureComponent<TargetControllerProps, TargetControllerState>(props),
    TargetStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val captureDelayOptions = listOf(0, 3, 10)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val restClient: ClientRestApi
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    TargetHeader::class.react {
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        navigationGlobal = this@Wrapper.navigationGlobal
                        block()
                    }
                }
            }
        }


        override fun body(): ReactWrapper<Props> {
            return object: ReactWrapper<Props> {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    TargetController::class.react {
                        clientStateGlobal = this@Wrapper.clientStateGlobal
                        mirroredGraphStore = this@Wrapper.mirroredGraphStore
                        navigationGlobal = this@Wrapper.navigationGlobal
                        restClient = this@Wrapper.restClient
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val store = TargetStore(
        props.clientStateGlobal,
        props.mirroredGraphStore,
        props.navigationGlobal,
        props.restClient
    )


    //-----------------------------------------------------------------------------------------------------------------
    override fun TargetControllerState.init(props: TargetControllerProps) {
        targetState = null
    }


    override fun componentDidMount() {
        store.observe(this)
        store.didMount()
    }


    override fun componentWillUnmount() {
        store.unobserve(this)
        store.willUnmount()
    }


    override fun onTargetState(targetState: TargetState) {
        setState {
            this.targetState = targetState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onSourceChange(sourceKey: String) {
        store.setSource(
            checkNotNull(TargetScreenshotSource.ofKeyOrNull(sourceKey)) { "Unknown source: $sourceKey" })
    }


    private fun onTraceScreenshotSelect(screenshot: BinaryValue) {
        store.selectTraceScreenshot(screenshot)
    }


    private fun onRemove(resourcePath: ResourcePath) {
        store.removeCrop(resourcePath)
    }


    private fun onToleranceChange(tolerance: Double) {
        store.setTolerance(tolerance)
    }


    private fun onSave(cropPng: ByteArray) {
        store.saveCrop(cropPng)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val targetState = state.targetState
            ?: return

        val resources = targetState.resources
            ?: return

        val section = TargetSection.active(targetState.parameters, targetState.hasCrops)

        div {
            css {
                padding = Padding(1.em, 1.em, 0.5.em, 1.em)
            }

            renderSourceControls(targetState)
            renderStatus(targetState)
        }

        when (section) {
            TargetSection.view ->
                TargetView::class.react {
                    documentPath = targetState.documentPath
                    this.resources = resources
                    restClient = props.restClient

                    screenshotDataUrl = targetState.screenshot.valueOrNull?.dataUrl
                    locateResult = targetState.locate.valueOrNull
                    locating = targetState.locate.phase == TargetFetchPhase.Requesting
                    tolerance = targetState.tolerance

                    onRemove = ::onRemove
                    onToleranceChange = ::onToleranceChange
                }

            TargetSection.add -> {
                val screenshotDataUrl = targetState.screenshot.valueOrNull?.dataUrl
                if (screenshotDataUrl != null) {
                    TargetAdd::class.react {
                        this.screenshotDataUrl = screenshotDataUrl
                        onSave = ::onSave
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderSourceControls(targetState: TargetState) {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                gap = 0.5.em
            }

            span {
                +"Screenshot:"
            }

            select {
                value = targetState.source.key
                onChange = {
                    onSourceChange(it.currentTarget.value)
                }

                option {
                    value = TargetScreenshotSource.Screen.key
                    +"Screen"
                }
                option {
                    value = TargetScreenshotSource.Browser.key
                    +"Browser (latest run)"
                }
            }

            if (targetState.source == TargetScreenshotSource.Screen) {
                span {
                    +"Delay:"
                }

                select {
                    value = targetState.captureDelaySeconds.toString()
                    onChange = {
                        store.setCaptureDelaySeconds(it.currentTarget.value.toInt())
                    }

                    for (delayOption in captureDelayOptions) {
                        option {
                            value = delayOption.toString()
                            +when (delayOption) {
                                0 -> "none"
                                else -> "$delayOption seconds"
                            }
                        }
                    }
                }
            }

            renderRefresh()
        }

        val traceScreenshots = targetState.trace.valueOrNull
        if (targetState.source == TargetScreenshotSource.Browser && !traceScreenshots.isNullOrEmpty()) {
            renderTraceStrip(traceScreenshots)
        }
    }


    private fun ChildrenBuilder.renderRefresh() {
        Button {
            sx {
                backgroundColor = NamedColor.white
            }
            variant = ButtonVariant.outlined
            size = Size.small

            onClick = { store.refresh() }

            icon("material-symbols:refresh") {
                style = unsafeJso {
                    marginRight = 0.25.em
                }
            }
            +"Refresh"
        }
    }


    private fun ChildrenBuilder.renderTraceStrip(
        traceScreenshots: List<BinaryValue>
    ) {
        div {
            css {
                display = Display.flex
                gap = 0.5.em
                marginTop = 0.5.em
                overflowX = Auto.auto
            }

            for (screenshot in traceScreenshots) {
                img {
                    css {
                        height = 5.em
                        cursor = Cursor.pointer
                        border = Border(1.px, LineStyle.solid, NamedColor.gray)
                    }

                    src = pngUrl(screenshot)

                    onClick = {
                        onTraceScreenshotSelect(screenshot)
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun ChildrenBuilder.renderStatus(targetState: TargetState) {
        val statusMessages = listOfNotNull(
            targetState.screenshot.errorMessageOrNull,
            targetState.trace.errorMessageOrNull,
            targetState.locate.errorMessageOrNull?.let { "Matching failed: $it" })

        if (statusMessages.isNotEmpty()) {
            for (statusMessage in statusMessages) {
                div {
                    css {
                        marginTop = 0.5.em
                        color = NamedColor.firebrick
                    }
                    +statusMessage
                }
            }
        }
        else if (targetState.screenshot.phase == TargetFetchPhase.Requesting) {
            div {
                css {
                    marginTop = 0.5.em
                    color = NamedColor.gray
                }
                +"Taking screenshot…"
            }
        }
    }
}
