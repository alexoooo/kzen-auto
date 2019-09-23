package tech.kzen.auto.client.objects.document.feature

import kotlinx.css.*
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.files.Blob
import org.w3c.files.FileReader
import react.*
import react.dom.br
import react.dom.img
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.NavigationManager
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeSuccess
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.AddResourceCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.resource.ResourceContent
import tech.kzen.lib.common.model.structure.resource.ResourceName
import tech.kzen.lib.common.model.structure.resource.ResourceNesting
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.DateTimeUtils
import tech.kzen.lib.platform.IoUtils


@Suppress("unused")
class FeatureController(
        props: Props
):
        RPureComponent<FeatureController.Props, FeatureController.State>(props),
        NavigationManager.Observer,
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val screenshotTakerLocation = ObjectLocation(
                DocumentPath.parse("auto-jvm/feature/feature.yaml"),
                ObjectPath(ObjectName("ScreenshotTaker"), ObjectNesting.root))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props: RProps


    class State(
            var documentPath: DocumentPath?,
            var graphStructure: GraphStructure?,

            var detail: CropperDetail?,
            var screenshotDataUrl: String?,
            var capturedDataUrl: String?,
            var requestingScreenshot: Boolean?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            private val archetype: ObjectLocation
    ):
            DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(FeatureController::class) {
//                attrs {
//                    this.attributeController = this@Wrapper.attributeController
//                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var cropperWrapper: CropperWrapper? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        documentPath = null
        graphStructure = null

        detail = null
        screenshotDataUrl = null
        capturedDataUrl = null
        requestingScreenshot = false
    }


    override fun componentDidMount() {
        async {
            ClientContext.mirroredGraphStore.observe(this)
            ClientContext.navigationManager.observe(this)
        }

        if (state.screenshotDataUrl == null) {
            setState {
                requestingScreenshot = true
            }
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationManager.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
        if (state.screenshotDataUrl == null && state.requestingScreenshot != true) {
            setState {
                requestingScreenshot = true
            }
        }

        if (state.requestingScreenshot == true && prevState.requestingScreenshot != true) {
            doRequestScreenshot()
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun handleNavigation(documentPath: DocumentPath?) {
        setState {
            this.documentPath = documentPath
        }
    }


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinition.successful.graphStructure
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinition.successful.graphStructure
        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    private fun doRequestScreenshot() {
        async {
//            console.log("doRequestScreenshot", screenshotTakerLocation.toString())
            val result= ClientContext.restClient.performDetached(
                    screenshotTakerLocation)

            if (result is ImperativeSuccess) {
                val screenshotPng = result.value as BinaryExecutionValue
                val base64 = IoUtils.base64Encode(screenshotPng.value)
                val screenshotPngUrl = "data:png/png;base64,$base64"

                setState {
                    screenshotDataUrl = screenshotPngUrl
                    requestingScreenshot = false
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onCrop(detail: CropperDetail) {
        setState {
            this.detail = detail
        }
    }


    private fun onSave() {
        val detail = state.detail
                ?: return

//        console.log("detail", detail)

        val canvas = cropperWrapper!!.getCroppedCanvas()

//        console.log("canvas", canvas)

        setState {
            capturedDataUrl = canvas.toDataURL()

            requestingScreenshot = true
        }

        canvas.toBlob({ blob: Blob? ->
            val fileReader = FileReader()

            fileReader.onload = { event ->
                val target  = event.target as FileReader
                val arrayBuffer= target.result as ArrayBuffer
                val uint8Array = Uint8Array(arrayBuffer)
                val byteArray = ByteArray(uint8Array.length) { i -> uint8Array[i] }

                async {
                    ClientContext.mirroredGraphStore.apply(AddResourceCommand(
                            ResourceLocation(
                                    state.documentPath!!,
                                    ResourcePath(
                                            ResourceName(DateTimeUtils.filenameTimestamp() + ".png"),
                                            ResourceNesting.empty
                                    )
                            ),
                            ResourceContent(byteArray)
                    ))
                }

                Unit
            }

            fileReader.readAsArrayBuffer(blob!!)
        })
    }


    private fun onCapture() {
        setState {
            capturedDataUrl = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val documentPath = state.documentPath
                ?: return

        val graphStructure = state.graphStructure
                ?: return

        val documentNotation = graphStructure.graphNotation.documents[documentPath]!!
        val resources = documentNotation.resources!!

        for (resource in resources.values) {
            +"resource: ${resource.key}"

            val resourceLocation = ResourceLocation(documentPath, resource.key)
            val resourceUri = ClientContext.restClient.resourceUri(resourceLocation)

            img {
                attrs {
                    src = resourceUri
                }
            }

            br {}
        }


        styledDiv {
            css {
                padding(1.em, 1.em, 0.px, 1.em)
            }

            val capturedDataUrl = state.capturedDataUrl
            val screenshotDataUrl = state.screenshotDataUrl
//                    ?: "screenshot.png"

//            +"requestingScreenshot: ${state.requestingScreenshot}"

            when {
                capturedDataUrl != null -> {
                    renderCapture()

                    br {}

                    renderDataUrl(capturedDataUrl)
                }

                screenshotDataUrl != null -> {
                    renderSave()

                    br {}

                    renderCropper(screenshotDataUrl)
                }

                else ->
                    +"<taking screenshot>"
            }
        }
    }


    private fun RBuilder.renderDataUrl(dataUrl: String) {
        img {
            attrs {
                src = dataUrl
            }
        }
    }


    private fun RBuilder.renderCapture() {
        styledDiv {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = ::onCapture

                    style = reactStyle {
                        backgroundColor = Color.white
                    }
                }

                child(CameraAltIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }
                +"Capture"
            }
        }
    }


    private fun RBuilder.renderSave() {
        styledDiv {
            child(MaterialButton::class) {
                attrs {
                    variant = "outlined"
                    size = "small"

                    onClick = ::onSave

                    style = reactStyle {
                        backgroundColor = Color.white
                    }
                }

                child(CameraAltIcon::class) {
                    attrs {
                        style = reactStyle {
                            marginRight = 0.25.em
                        }
                    }
                }
                +"Save"
            }
        }
    }


    private fun RBuilder.renderCropper(
            screenshotDataUrl: String
    ) {
        styledDiv {
            css {
                width = 100.pct
                height = 100.vh.minus(9.em)
                minHeight = 200.px
                maxHeight = 1024.px
            }

            child(CropperWrapper::class) {
                attrs {
                    src = screenshotDataUrl

                    crop = {event ->
                        @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                        val detail = event.detail as CropperDetail
                        onCrop(detail)
                    }
                }

                ref {
                    cropperWrapper = it as? CropperWrapper
                }
            }
        }
    }
}