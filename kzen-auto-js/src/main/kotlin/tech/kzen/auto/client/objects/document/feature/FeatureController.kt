package tech.kzen.auto.client.objects.document.feature

import csstype.*
import emotion.react.css
import js.core.jso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.*
import react.dom.html.ReactHTML.br
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.hr
import react.dom.html.ReactHTML.img
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.cropper.CropperDetail
import tech.kzen.auto.client.wrap.cropper.CropperWrapper
import tech.kzen.auto.client.wrap.material.CameraAltIcon
import tech.kzen.auto.client.wrap.material.DeleteIcon
import tech.kzen.auto.client.wrap.material.RefreshIcon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.paradigm.common.model.BinaryExecutionValue
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.util.RequestParams
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.locate.ResourceLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.model.structure.resource.ResourceName
import tech.kzen.lib.common.model.structure.resource.ResourceNesting
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.util.ImmutableByteArray
import tech.kzen.lib.platform.DateTimeUtils
import tech.kzen.lib.platform.IoUtils


//---------------------------------------------------------------------------------------------------------------------
external interface FeatureControllerState: react.State {
    var documentPath: DocumentPath?
    var graphStructure: GraphStructure?

    var detail: CropperDetail?
    var screenshotDataUrl: String?
    var capturedDataUrl: String?
    var requestingScreenshot: Boolean?
}


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class FeatureController(
        props: Props
):
        RPureComponent<Props, FeatureControllerState>(props),
        NavigationGlobal.Observer,
        LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        private val archetype: ObjectLocation
    ):
        DocumentController
    {
        override fun archetypeLocation(): ObjectLocation {
            return archetype
        }


        override fun header(): ReactWrapper<react.Props> {
            return object: ReactWrapper<react.Props> {
//                override fun child(builder: ChildrenBuilder, block: Props.() -> Unit) {}
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {}
            }
        }


        override fun body(): ReactWrapper<react.Props> {
            return object: ReactWrapper<react.Props> {
//                override fun child(builder: ChildrenBuilder, block: Props.() -> Unit) {
                override fun ChildrenBuilder.child(block: Props.() -> Unit) {
                    FeatureController::class.react {
                        block()
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var cropperWrapper: RefObject<CropperWrapper> = createRef()
    private var screenshotBytes: ByteArray? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun FeatureControllerState.init(props: Props) {
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
            ClientContext.navigationGlobal.observe(this)
        }

        if (state.screenshotDataUrl == null) {
            setState {
                requestingScreenshot = true
            }
        }
    }


    override fun componentWillUnmount() {
        ClientContext.mirroredGraphStore.unobserve(this)
        ClientContext.navigationGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: FeatureControllerState,
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
    override fun handleNavigation(
            documentPath: DocumentPath?,
            parameters: RequestParams
    ) {
        setState {
            this.documentPath = documentPath
        }
    }


    override suspend fun onCommandSuccess(event: NotationEvent, graphDefinition: GraphDefinitionAttempt) {
        if ((event is DeletedDocumentEvent || event is RenamedDocumentRefactorEvent) &&
                event.documentPath == state.documentPath) {
            return
        }

        setState {
            this.graphStructure = graphDefinition.graphStructure
        }
    }


    override suspend fun onCommandFailure(command: NotationCommand, cause: Throwable) {}


    override suspend fun onStoreRefresh(graphDefinition: GraphDefinitionAttempt) {
        setState {
            this.graphStructure = graphDefinition.graphStructure
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun doRequestScreenshot() {
        async {
//            console.log("doRequestScreenshot", screenshotTakerLocation.toString())
            val result = ClientContext.restClient.performDetached(
                FeatureDocument.screenshotTakerLocation)

            if (result is ExecutionSuccess) {
                val screenshotPng = result.value as BinaryExecutionValue
                val base64 = IoUtils.base64Encode(screenshotPng.value)
                val screenshotPngUrl = "data:png/png;base64,$base64"

                screenshotBytes = screenshotPng.value

                setState {
                    screenshotDataUrl = screenshotPngUrl
                    requestingScreenshot = false
                }
            }
        }
    }


    private fun doCropAndSave(detail: CropperDetail) {
        async {
            val result = ClientContext.restClient.performDetached(
                FeatureDocument.screenshotCropperLocation,
                screenshotBytes!!,
                FeatureDocument.cropTopParam to detail.y.toInt().toString(),
                FeatureDocument.cropLeftParam to detail.x.toInt().toString(),
                FeatureDocument.cropWidthParam to detail.width.toInt().toString(),
                FeatureDocument.cropHeightParam to detail.height.toInt().toString())

            if (result !is ExecutionSuccess) {
                return@async
            }
            val cropPng = result.value as BinaryExecutionValue

            ClientContext.mirroredGraphStore.apply(AddResourceCommand(
                ResourceLocation(
                    state.documentPath!!,
                    ResourcePath(
                        ResourceName(DateTimeUtils.filenameTimestamp() + ".png"),
                        ResourceNesting.empty)),
                ImmutableByteArray.wrap(cropPng.value)
            ))

            onRefresh()

//            val screenshotPng = result.value as BinaryExecutionValue
//            val base64 = IoUtils.base64Encode(screenshotPng.value)
//            val screenshotPngUrl = "data:png/png;base64,$base64"
//
//            screenshotBytes = screenshotPng.value
//
//            setState {
//                screenshotDataUrl = screenshotPngUrl
//                requestingScreenshot = false
//            }
        }


//        canvas.toBlob({ blob: Blob? ->
//            val fileReader = FileReader()
//
//            fileReader.onload = { event ->
//                val target  = event.target as FileReader
//                val arrayBuffer= target.result as ArrayBuffer
//                val uint8Array = Uint8Array(arrayBuffer)
//                val byteArray = ByteArray(uint8Array.length) { i -> uint8Array[i] }
//
//                async {
//                    ClientContext.mirroredGraphStore.apply(AddResourceCommand(
//                            ResourceLocation(
//                                    state.documentPath!!,
//                                    ResourcePath(
//                                            ResourceName(DateTimeUtils.filenameTimestamp() + ".png"),
//                                            ResourceNesting.empty
//                                    )
//                            ),
//                            ImmutableByteArray.wrap(byteArray)
//                    ))
//
//                    onRefresh()
//                }
//
//                Unit
//            }
//
//            fileReader.readAsArrayBuffer(blob!!)
//        })

    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRefresh() {
        screenshotBytes = null
        setState {
            capturedDataUrl = null
            screenshotDataUrl = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onRemove(resourcePath: ResourcePath) {
        async {
            ClientContext.mirroredGraphStore.apply(RemoveResourceCommand(
                ResourceLocation(
                    state.documentPath!!,
                    resourcePath)
            ))
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

        val canvas = cropperWrapper.current!!.getCroppedCanvas()

//        console.log("canvas", canvas)

        setState {
            capturedDataUrl = canvas.toDataURL()

            requestingScreenshot = true
        }

        doCropAndSave(detail)
    }


    private fun onCapture() {
        setState {
            capturedDataUrl = null
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val documentPath = state.documentPath
            ?: return

        val graphStructure = state.graphStructure
            ?: return

        val documentNotation = graphStructure.graphNotation.documents[documentPath]!!
        val resources = documentNotation.resources!!

        div {
            css {
                padding = 1.em
            }

            for (resource in resources.digests) {
                val resourceLocation = ResourceLocation(documentPath, resource.key)
                val resourceUri = ClientContext.restClient.resourceUri(resourceLocation)

                img {
                    src = resourceUri
                }

                span {
                    css {
                        marginLeft = 1.em
                    }
                    renderDelete(resource.key)
                }

                hr {}
            }
        }


        div {
            css {
                padding = Padding(1.em, 1.em, 0.px, 1.em)
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
                    div {
                        css {
                            marginBottom = 0.5.em
                        }
                        renderSave()
                        renderRefresh()
                    }
                    renderCropper(screenshotDataUrl)
                }

                else ->
                    +"<taking screenshot>"
            }
        }
    }


    private fun ChildrenBuilder.renderDataUrl(dataUrl: String) {
        img {
            src = dataUrl
        }
    }


    private fun ChildrenBuilder.renderCapture() {
        div {
            Button {
                css {
                    backgroundColor = NamedColor.white
                }
                variant = ButtonVariant.outlined
                size = Size.small

                onClick = { onCapture() }

                CameraAltIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }
                +"Capture"
            }
        }
    }


    private fun ChildrenBuilder.renderSave() {
        div {
            css {
                display = Display.inlineBlock
            }

            Button {
                css {
                    backgroundColor = NamedColor.white
                }

                variant = ButtonVariant.outlined
                size = Size.small

                onClick = { onSave() }

                CameraAltIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }
                +"Save"
            }
        }
    }


    private fun ChildrenBuilder.renderRefresh() {
        div {
            css {
                display = Display.inlineBlock
            }

            Button {
                css {
                    backgroundColor = NamedColor.white
                }
                variant = ButtonVariant.outlined
                size = Size.small

                onClick = { onRefresh() }

                RefreshIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }
                +"Refresh Screenshot"
            }
        }
    }


    private fun ChildrenBuilder.renderDelete(
            resourcePath: ResourcePath
    ) {
        div {
            css {
                display = Display.inlineBlock
            }

            Button {
                css {
                    backgroundColor = NamedColor.white
                }
                variant = ButtonVariant.outlined
                size = Size.small

                onClick = {
                    onRemove(resourcePath)
                }

                title = "Delete"

                DeleteIcon::class.react {
                    style = jso {
                        marginRight = 0.25.em
                    }
                }
            }
        }
    }


    private fun ChildrenBuilder.renderCropper(
            screenshotDataUrl: String
    ) {
        div {
            css {
                width = 100.pct
                height = 100.vh.minus(10.em)
                minHeight = 200.px
                maxHeight = 1024.px
            }

            CropperWrapper::class.react {
                src = screenshotDataUrl

                crop = { event ->
                    @Suppress("UNCHECKED_CAST_TO_EXTERNAL_INTERFACE")
                    val detail = event.detail as CropperDetail
                    onCrop(detail)
                }

                ref = cropperWrapper
            }
        }
    }
}