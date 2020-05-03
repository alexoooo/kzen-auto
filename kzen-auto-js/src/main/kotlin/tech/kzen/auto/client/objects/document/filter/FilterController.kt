package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.*
import react.dom.br
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
import tech.kzen.auto.common.paradigm.reactive.ValueSummary
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

        val columnDomainLocation = ObjectLocation(
                filterJvmPath,
                ObjectPath(ObjectName("ColumnDomain"), ObjectNesting.root))
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props: RProps


    class State(
            var clientState: SessionState?,

            var error: String?,

            var columnListingLoading: Boolean,
            var columnListing: List<String>?,

            var writingOutput: Boolean,
            var wroteOutputPath: String?,

            var columnDetails: List<ValueSummary>?
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

        columnDetails = null
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

        val columnListing = state.columnListing
        if (columnListing == null && ! state.columnListingLoading) {
            setState {
                columnListingLoading = true
            }
        }
        else if (columnListing != null && prevState.columnListing == null) {
            if (columnListing.isNotEmpty()) {
                requestNextColumn(mainLocation()!!, clientState, 0)
            }
        }
        else if (state.columnDetails?.size != prevState.columnDetails?.size &&
                state.columnDetails?.size ?: 0 < columnListing?.size ?: 0)
        {
            val nextIndex = state.columnDetails!!.size
            requestNextColumn(mainLocation()!!, clientState, nextIndex)
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
                    FilterDocument.inputKey to inputValue,
                    FilterDocument.outputKey to outputValue)

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


    private fun requestNextColumn(
            mainLocation: ObjectLocation,
            clientState: SessionState,
            columnIndex: Int
    ) {
//        val columnListing = state.columnListing
//                ?: return

        val graphStructure = clientState.graphStructure()

        val inputValue = graphStructure
                .graphNotation
                .transitiveAttribute(mainLocation, FilterDocument.inputAttribute)
                ?.asString()
                ?: return

        async {
            val result = ClientContext.restClient.performDetached(
                    columnDomainLocation,
                    FilterDocument.inputKey to inputValue,
                    FilterDocument.indexKey to columnIndex.toString())

            when (result) {
                is ExecutionSuccess -> {
                    val columnDetails = state.columnDetails
                            ?: emptyList()

                    @Suppress("UNCHECKED_CAST")
                    val valueSummary = ValueSummary.fromCollection(
                        result.value.get() as Map<String, Any>)

                    setState {
                        this.columnDetails = columnDetails + valueSummary
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
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


    //-----------------------------------------------------------------------------------------------------------------
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


    //-----------------------------------------------------------------------------------------------------------------
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


    //-----------------------------------------------------------------------------------------------------------------
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
        +"${index + 1} - $columnName "

        val columnDetails = state.columnDetails

        if (columnDetails?.size ?: 0 > index) {
            val valueSummary = columnDetails!![index]

            +"count: ${valueSummary.count}"
            br {}

            val histogram = valueSummary.nominalValueSummary.histogram
            if (histogram.isNotEmpty()) {
                +"histogram: $histogram"
                br {}
            }

            val density = valueSummary.numericValueSummary.density
            if (density.isNotEmpty()) {
                +"density: $density"
                br {}
            }

            val sample = valueSummary.opaqueValueSummary.sample
            if (sample.isNotEmpty()) {
                +"sample: $sample"
                br {}
            }
        }
        else {
            +"..."
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
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