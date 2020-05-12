package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.*
import react.dom.li
import react.dom.ol
import styled.css
import styled.styledDiv
import styled.styledSpan
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.objects.document.common.DefaultAttributeEditor
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.reactive.ValueSummary
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
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
    class Props: RProps


    class State(
            var clientState: SessionState?,

            var error: String?,

            var fileListingLoading: Boolean,
            var fileListing: List<String>?,

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

        fileListingLoading = false
        fileListing = null

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

        if (state.fileListingLoading && ! prevState.fileListingLoading) {
            getFileListing()
        }

        if (state.columnListingLoading && ! prevState.columnListingLoading) {
            getColumnListing()
        }

        val fileListing = state.fileListing
        if (fileListing == null && ! state.fileListingLoading) {
            setState {
                fileListingLoading = true
            }
            return
        }

        val columnListing = state.columnListing
        if (columnListing == null && ! state.columnListingLoading) {
            setState {
                columnListingLoading = true
            }
            return
        }

        if (columnListing != null && prevState.columnListing == null) {
            if (columnListing.isNotEmpty()) {
                requestNextColumn(mainLocation()!!, columnListing[0])
            }
            return
        }

        if (state.columnDetails?.size != prevState.columnDetails?.size &&
                state.columnDetails?.size ?: 0 < columnListing?.size ?: 0)
        {
            val nextIndex = state.columnDetails!!.size
            requestNextColumn(mainLocation()!!, columnListing!![nextIndex])
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: SessionState) {
        setState {
            this.clientState = clientState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun getFileListing() {
        val mainLocation = mainLocation()!!

        async {
            val result = ClientContext.restClient.performDetached(
                mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionFiles)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as List<String>

                    setState {
                        fileListing = resultValue
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            setState {
                fileListingLoading = false
            }
        }
    }


    private fun getColumnListing() {
        val mainLocation = mainLocation()!!

        async {
            val result = ClientContext.restClient.performDetached(
                mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionColumns)

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


    private fun applyFilter(mainLocation: ObjectLocation) {
        if (state.writingOutput) {
            return
        }

        setState {
            writingOutput = true
        }

        async {
            val result = ClientContext.restClient.performDetached(
                mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionApply)

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
            columnName: String
    ) {
        async {
            val result = ClientContext.restClient.performDetached(
                mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionSummary,
                FilterConventions.columnKey to columnName)

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
        val clientState = state.clientState
                ?: return null

        val documentPath = clientState
                .navigationRoute
                .documentPath
                ?: return null

        val documentNotation = clientState.graphStructure().graphNotation.documents[documentPath]
                ?: return null

        if (! FilterConventions.isFeature(documentNotation)) {
            return null
        }

        return ObjectLocation(documentPath, NotationConventions.mainObjectPath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val clientState = state.clientState
                ?: return

        val mainLocation = mainLocation()
                ?: return

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

            renderFiles()
            renderSpacing()

            renderFilters(mainLocation, clientState)
        }

        renderRun(mainLocation)
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
                attributeName = FilterConventions.inputAttribute
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
                attributeName = FilterConventions.outputAttribute
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderProgress() {
        styledDiv {
            css {
                fontSize = 2.em
                fontFamily = "monospace"
                fontWeight = FontWeight.bold
                backgroundColor = Color("rgba(255, 255, 255, 0.5)")
                padding(0.5.em)
//                margin(1.em)
            }

            +"Status -> "

            val error = state.error
            if (error != null) {
                +"Error: $error"
                return
            }

            if (state.fileListingLoading) {
                +"Working: listing files"
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

            +"Showing columns (${state.columnDetails?.size ?: 0} of ${state.columnListing?.size})"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderFiles() {
        val fileListing = state.fileListing

        child(MaterialPaper::class) {
            child(MaterialCardContent::class) {
                styledSpan {
                    css {
                        fontSize = 2.em
                    }

                    +"Input Files"
                }

                if (fileListing == null) {
                    +"..."
                }
                else {
                    ol {
                        for (filePath in fileListing) {
                            li {
                                attrs {
                                    key = filePath
                                }
                                +filePath
                            }
                        }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderFilters(
        mainLocation: ObjectLocation,
        clientState: SessionState
    ) {
        val columnListing = state.columnListing

        if (columnListing == null) {
            +"..."
            return
        }

        val criteriaDefinition = clientState
            .graphDefinitionAttempt
            .successful
            .objectDefinitions[mainLocation]!!
            .attributeDefinitions[FilterConventions.criteriaAttributeName]!!
        val criteriaSpec = (criteriaDefinition as ValueAttributeDefinition).value as CriteriaSpec

        val columnDetails = state.columnDetails

        for (index in columnListing.indices) {
            styledDiv {
                key = index.toString()

                css {
                    marginBottom = 1.em
                }

                // val graphStructure = clientState.graphStructure()
                val columnName = columnListing[index]

                child(ColumnFilter::class) {
                    attrs {
                        this.mainLocation = mainLocation
                        this.clientState = clientState
//                        this.criteriaSpec = criteriaSpec
                        this.requiredValues = criteriaSpec.columnRequiredValues[columnName]

                        columnIndex = index
                        columnHeader = columnName
                        valueSummary =
                            if (columnDetails?.size ?: 0 > index) {
                                columnDetails!![index]
                            }
                            else {
                                null
                            }
                    }
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderRun(
            mainLocation: ObjectLocation
    ) {
        styledDiv {
            css {
                position = Position.fixed
                bottom = 0.px
                right = 0.px
                marginRight = 2.em
                marginBottom = 2.em
            }

            renderRunFab(mainLocation)
        }
    }


    private fun RBuilder.renderRunFab(
            mainLocation: ObjectLocation
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

            if (state.writingOutput) {
                child(MaterialCircularProgress::class) {}
            }
            else {
                child(PlayArrowIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }

                        onClick = {
                            applyFilter(mainLocation)
                        }
                    }
                }
            }
        }
    }
}