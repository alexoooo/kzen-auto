package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
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
import tech.kzen.auto.client.wrap.MaterialFab
import tech.kzen.auto.client.wrap.PlayArrowIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.filter.FilterDocument
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
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
        private val filterJvmPath = DocumentPath.parse("auto-jvm/filter/filter-jvm.yaml")

        val columnListingLocation = ObjectLocation(
                filterJvmPath,
                ObjectPath(ObjectName("ColumnListing"), ObjectNesting.root))

        val applyFilterLocation = ObjectLocation(
                filterJvmPath,
                ObjectPath(ObjectName("ApplyFilter"), ObjectNesting.root))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props: RProps


    class State(
            var clientState: SessionState?,

            var error: String?,

            var columnListingLoading: Boolean,
            var columnListing: List<String>?,

            var writingOutput: Boolean,
            var wroteOutputPath: String?
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

        error = null

        columnListingLoading = false
        columnListing = null

        writingOutput = false
        wroteOutputPath = null
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
        if (state.error != null) {
            return
        }

        val clientState = state.clientState
                ?: return

        if (state.columnListingLoading && ! prevState.columnListingLoading) {
            getColumnListing(clientState)
        }

        if (state.columnListing == null && ! state.columnListingLoading) {
            setState {
                columnListingLoading = true
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getColumnListing(clientState: SessionState) {
        val graphStructure = clientState.graphStructure()

        val inputValue = graphStructure
                .graphNotation
                .transitiveAttribute(mainLocation()!!, FilterDocument.inputAttribute)
                ?.asString()
                ?: return

        async {
            val result= ClientContext.restClient.performDetached(
                    columnListingLocation,
                    FilterDocument.inputAttribute.value to inputValue)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as List<String>

                    setState {
                        columnListing = resultValue
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            setState {
                columnListingLoading = false
            }
        }
    }


    private fun applyFilter(mainLocation: ObjectLocation, clientState: SessionState) {
        if (state.writingOutput) {
            return
        }

        val graphStructure = clientState.graphStructure()

        val inputValue = graphStructure
                .graphNotation
                .transitiveAttribute(mainLocation, FilterDocument.inputAttribute)
                ?.asString()
                ?: return

        val outputValue = graphStructure
                .graphNotation
                .transitiveAttribute(mainLocation, FilterDocument.outputAttribute)
                ?.asString()
                ?: return

        setState {
            writingOutput = true
        }

        async {
            val result = ClientContext.restClient.performDetached(
                    applyFilterLocation,
                    FilterDocument.inputAttribute.value to inputValue,
                    FilterDocument.outputAttribute.value to outputValue)

            when (result) {
                is ExecutionSuccess -> {
                    setState {
                        wroteOutputPath = result.value.get() as String
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            setState {
                writingOutput = false
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainLocation(): ObjectLocation? {
        return state
                .clientState
                ?.navigationRoute
                ?.documentPath
                ?.let { ObjectLocation(it, NotationConventions.mainObjectPath) }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
                ?: return

        val mainLocation = mainLocation()!!

        styledDiv {
            css {
                padding(1.em)
            }

            renderInput(mainLocation, clientState)

            renderSpacing()

            renderOutput(mainLocation, clientState)

            renderSpacing()

            renderProgress()

            renderSpacing()

            renderFilters()
        }

        renderRun(mainLocation, clientState)
    }


    private fun RBuilder.renderSpacing() {
        styledDiv {
            css {
                height = 1.em
            }
        }
    }


    private fun RBuilder.renderInput(
            mainLocation: ObjectLocation,
            clientState: SessionState
    ) {
        child(DefaultAttributeEditor::class) {
            attrs {
                this.clientState = clientState
                objectLocation = mainLocation
                attributeName = FilterDocument.inputAttribute
            }
        }
    }


    private fun RBuilder.renderOutput(
            mainLocation: ObjectLocation,
            clientState: SessionState
    ) {
        child(DefaultAttributeEditor::class) {
            attrs {
                this.clientState = clientState
                objectLocation = mainLocation
                attributeName = FilterDocument.outputAttribute
            }
        }
    }


    private fun RBuilder.renderProgress() {
        val error = state.error
        if (error != null) {
            +"Error: $error"
            return
        }

        if (state.columnListingLoading) {
            +"Working: listing columns"
            return
        }

        if (state.writingOutput) {
            +"Working: writing output"
            return
        }

        val wrotePath = state.wroteOutputPath
        if (wrotePath != null) {
            +"Wrote: $wrotePath"
            return
        }

        +"Showing columns"
    }


    private fun RBuilder.renderFilters() {
        val columnListing = state.columnListing

        if (columnListing == null) {
            +"..."
            return
        }

        for (i in columnListing.indices) {
            styledDiv {
                key = i.toString()

                renderFilter(i, columnListing[i])
            }
        }
    }


    private fun RBuilder.renderFilter(index: Int, columnName: String) {
        +"${index + 1} - $columnName"
    }


    private fun RBuilder.renderRun(
            mainLocation: ObjectLocation,
            clientState: SessionState
    ) {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            renderRunFab(mainLocation, clientState)
        }
    }


    private fun RBuilder.renderRunFab(
            mainLocation: ObjectLocation,
            clientState: SessionState
    ) {
        child(MaterialFab::class) {
            attrs {
                title = when {
                    state.writingOutput ->
                        "Running..."

                    else ->
                        "Run"
                }

                style = reactStyle {
                    backgroundColor =
                            if (state.writingOutput) {
                                Color.white
                            }
                            else {
                                Color.gold
                            }

                    width = 5.em
                    height = 5.em
                }
            }

            child(PlayArrowIcon::class) {
                attrs {
                    style = reactStyle {
                        fontSize = 3.em
                    }

                    onClick = {
                        applyFilter(mainLocation, clientState)
                    }
                }
            }
        }
    }
}