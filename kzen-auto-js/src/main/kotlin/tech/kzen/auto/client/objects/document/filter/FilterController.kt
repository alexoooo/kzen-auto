package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.SessionGlobal
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.CropperWrapper
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.common.objects.document.filter.FilterConventions
import tech.kzen.auto.common.paradigm.common.model.ExecutionFailure
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.auto.common.paradigm.reactive.SummaryProgress
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.util.RequestParams
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

        var initialTableSummaryLoading: Boolean,
        var tableSummaryLoading: Boolean,
        var tableSummaryProgress: SummaryProgress?,
        var tableSummary: TableSummary?,

        var writingOutput: Boolean,
        var wroteOutputPath: String?,

        var inputChanged: Boolean
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

    private var tableSummaryProgressDebounce: FunctionWithDebounce = lodash.debounce({
        updateTableSummaryProgress()
    }, 5_000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        clientState = null

        error = null

        initialTableSummaryLoading = false
        tableSummaryLoading = false
        tableSummaryProgress = null
        tableSummary = null

        writingOutput = false
        wroteOutputPath = null

        inputChanged = false
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
        val clientState = state.clientState
            ?: return
        val mainLocation = mainLocation(clientState)
            ?: return

        if (state.error != null) {
            return
        }

        if (state.initialTableSummaryLoading && ! prevState.initialTableSummaryLoading) {
            getInitialTableSummary()
        }

//        if (state.tableSummaryLoading && ! prevState.tableSummaryLoading) {
//            getTableSummary()
//        }

        val tableSummary = state.tableSummary
        if (tableSummary == null && ! state.initialTableSummaryLoading) {
            setState {
                initialTableSummaryLoading = true
            }
        }
//        if (tableSummary == null && ! state.tableSummaryLoading) {
//            setState {
//                tableSummaryLoading = true
//            }
//        }

        val prevClientState = prevState.clientState
            ?: return
        if (inputPath(clientState, mainLocation) != inputPath(prevClientState, mainLocation)) {
            setState {
                inputChanged = true
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
    private fun getInitialTableSummary() {
        val mainLocation = mainLocation()!!

        async {
            val result = ClientContext.restClient.performDetached(
                mainLocation,
                FilterConventions.actionParameter to FilterConventions.actionSummaryLookup)

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as Map<String, Map<String, Any>>
                    val tableSummary = TableSummary.fromCollection(resultValue)

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>
                    val summaryProgress = SummaryProgress.fromCollection(resultDetail)

                    setState {
                        this.tableSummary = tableSummary
                        this.tableSummaryProgress = summaryProgress
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            setState {
                initialTableSummaryLoading = false
            }
        }
    }


    private fun getTableSummary() {
        val mainLocation = mainLocation()!!

        async {
            val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(mainLocation)

            val taskModel: TaskModel =
                if (activeTasks.isNotEmpty()) {
                    check(activeTasks.size == 1)
                    val taskId = activeTasks.first()
                    ClientContext.clientRestTaskRepository.query(taskId)!!
                }
                else {
                    ClientContext.clientRestTaskRepository.submit(
                        mainLocation,
                        DetachedRequest(
                            RequestParams.of(
                                FilterConventions.actionParameter to FilterConventions.actionSummaryRun),
                            null))
                }

            val result =
                taskModel.finalResult ?: taskModel.partialResult!!

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as Map<String, Map<String, Any>>
                    val tableSummary = TableSummary.fromCollection(resultValue)

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val tableSummaryProgress = resultDetail?.let { SummaryProgress.fromCollection(it) }

                    setState {
                        this.tableSummaryProgress = tableSummaryProgress
                        this.tableSummary = tableSummary
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            val isDone = taskModel.finalResult != null

            setState {
                tableSummaryLoading = ! isDone
            }

            if (! isDone) {
                tableSummaryProgressDebounce.apply()
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
                FilterConventions.actionParameter to FilterConventions.actionFilter)

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


    private fun refresh() {
        setState {
            error = null

//            fileListingLoading = false
//            fileListing = null

//            columnListingLoading = false
//            columnListing = null

            writingOutput = false
            wroteOutputPath = null

            inputChanged = false
        }
    }


    private fun updateTableSummaryProgress() {
        if (! state.tableSummaryLoading) {
            return
        }

        val mainLocation = mainLocation()!!

        async {
            val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(mainLocation)
            if (activeTasks.isEmpty()) {
                return@async
            }

            check(activeTasks.size == 1)
            val taskId = activeTasks.first()
            val taskModel: TaskModel = ClientContext.clientRestTaskRepository.query(taskId)!!

            val result =
                taskModel.finalResult ?: taskModel.partialResult!!

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as Map<String, Map<String, Any>>
                    val tableSummary = TableSummary.fromCollection(resultValue)

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val tableSummaryProgress = resultDetail?.let { SummaryProgress.fromCollection(it) }

                    setState {
                        if (this.tableSummaryProgress != tableSummaryProgress) {
                            this.tableSummaryProgress = tableSummaryProgress
                        }

                        if (this.tableSummary != tableSummary) {
                            this.tableSummary = tableSummary
                        }
                    }
                }

                is ExecutionFailure -> {
                    setState {
                        error = result.errorMessage
                    }
                }
            }

            val isDone = taskModel.finalResult != null

            setState {
                tableSummaryLoading = ! isDone
            }

            if (! isDone) {
                tableSummaryProgressDebounce.apply()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun mainLocation(): ObjectLocation? {
        val clientState = state.clientState
            ?: return null

        return mainLocation(clientState)
    }


    private fun mainLocation(clientState: SessionState): ObjectLocation? {
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


    private fun inputPath(clientState: SessionState, mainLocation: ObjectLocation): String {
        val mainObjectNotation = clientState
            .graphStructure().graphNotation.documents[mainLocation.documentPath]!!

        return FilterConventions.getInput(mainObjectNotation)
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

            renderInputs(mainLocation, clientState)

            renderSpacing()

            renderOutput(mainLocation, clientState)

            renderSpacing()

            renderStatus(mainLocation, clientState)

            renderSpacing()

            renderColumnList(mainLocation, clientState)
        }

        renderRun(mainLocation, clientState)
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderInputs(
        mainLocation: ObjectLocation,
        clientState: SessionState
    ) {
        child(FilterInputs::class) {
            attrs {
                this.mainLocation = mainLocation
                this.clientState = clientState
            }
        }
    }


    private fun RBuilder.renderOutput(
        mainLocation: ObjectLocation,
        clientState: SessionState
    ) {
        child(FilterOutput::class) {
            attrs {
                this.mainLocation = mainLocation
                this.clientState = clientState
            }
        }
    }


    private fun RBuilder.renderStatus(
        mainLocation: ObjectLocation,
        clientState: SessionState
    ) {
        child(FilterStatus::class) {
            attrs {
                this.mainLocation = mainLocation
                this.clientState = clientState
                this.error = state.error
                this.initialTableSummaryLoading = state.initialTableSummaryLoading
                this.summaryProgress = state.tableSummaryProgress
            }
        }
    }


    private fun RBuilder.renderColumnList(
        mainLocation: ObjectLocation,
        clientState: SessionState
    ) {
        child(FilterColumnList::class) {
            attrs {
                this.mainLocation = mainLocation
                this.clientState = clientState
                this.tableSummary = state.tableSummary
            }
        }
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

            child(FilterRun::class) {
                attrs {
                    this.mainLocation = mainLocation
                    this.clientState = clientState
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun RBuilder.renderSpacing() {
        styledDiv {
            css {
                height = 1.em
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
//    private fun RBuilder.renderProgress() {
//        styledDiv {
//            css {
//                backgroundColor = Color("rgba(255, 255, 255, 0.5)")
//                padding(0.5.em)
//            }
//
//            if (state.error != null) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Error: ${state.error!!}"
//                }
//            }
//            else if (state.columnListingLoading) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Working: listing columns"
//                }
//            }
//            else if (state.tableSummaryLoading) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Working: indexing column contents"
//                }
//
//                val remainingFiles = state.tableSummaryProgress?.remainingFiles
//                if (remainingFiles != null) {
//                    table {
//                        thead {
//                            tr {
//                                th { +"File" }
//                                th { +"Progress" }
//                            }
//                        }
//                        tbody {
//                            for (e in remainingFiles.entries) {
//                                tr {
//                                    key = e.key
//
//                                    td {
//                                        +e.key
//                                    }
//                                    td {
//                                        +e.value
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//            else if (state.writingOutput) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Working: writing output"
//                }
//            }
//            else if (state.wroteOutputPath != null) {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Wrote: ${state.wroteOutputPath!!}"
//                }
//            }
//            else {
//                styledSpan {
//                    css {
//                        fontSize = 2.em
//                        fontFamily = "monospace"
//                        fontWeight = FontWeight.bold
//                    }
//                    +"Showing columns (${state.columnListing?.size})"
//                }
//            }
//        }
//    }
}