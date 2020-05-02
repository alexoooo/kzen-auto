package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.em
import kotlinx.css.padding
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.CropperWrapper
import tech.kzen.auto.common.objects.document.filter.FilterDocument
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.service.notation.NotationConventions


@Suppress("unused")
class FilterController(
        props: Props
):
        RPureComponent<FilterController.Props, FilterController.State>(props),
        SessionGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val screenshotTakerLocation = ObjectLocation(
//                DocumentPath.parse("auto-jvm/feature/feature.yaml"),
//                ObjectPath(ObjectName("ScreenshotTaker"), ObjectNesting.root))
//
//
//        val screenshotCropperLocation = ObjectLocation(
//                DocumentPath.parse("auto-jvm/feature/feature.yaml"),
//                ObjectPath(ObjectName("ScreenshotCropper"), ObjectNesting.root))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props: RProps


    class State(
            var clientState: SessionState?
//            var documentPath: DocumentPath?,
//            var graphStructure: GraphStructure?

//            var detail: CropperDetail?,
//            var screenshotDataUrl: String?,
//            var capturedDataUrl: String?,
//            var requestingScreenshot: Boolean?
    ): RState


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

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(FilterController::class) {
//                attrs {
//                    this.attributeController = this@Wrapper.attributeController
//                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var cropperWrapper: CropperWrapper? = null
    private var screenshotBytes: ByteArray? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        clientState = null
    }


    override fun componentDidMount() {
        async {
            ClientContext.sessionGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
//        if (state.screenshotDataUrl == null && state.requestingScreenshot != true) {
//            setState {
//                requestingScreenshot = true
//            }
//        }
//
//        if (state.requestingScreenshot == true && prevState.requestingScreenshot != true) {
//            doRequestScreenshot()
//        }
    }



    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
                ?: return

        val documentPath = clientState.navigationRoute.documentPath
                ?: return

//        val graphStructure = clientState.graphStructure()

//        +"Hello! ${documentPath.name} | ${graphStructure.graphNotation.documents[documentPath]?.objects?.notations?.values?.keys}"

        styledDiv {
            css {
//                margin(1.em)
                padding(1.em)
            }

            child(DefaultAttributeEditor::class) {
                attrs {
                    this.clientState = clientState
                    objectLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
                    attributeName = FilterDocument.inputPatternAttribute
                }
            }
        }
    }
}