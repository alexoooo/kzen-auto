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
import tech.kzen.auto.common.paradigm.reactive.TableSummary
import tech.kzen.auto.common.paradigm.reactive.TaskProgress
import tech.kzen.auto.common.paradigm.task.model.TaskId
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.model.TaskState
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
        var tableSummaryTaskRunning: Boolean,
        var tableSummaryTaskId: TaskId?,
        var tableSummaryTaskProgress: TaskProgress?,
        var tableSummaryTaskState: TaskState?,
        var tableSummary: TableSummary?,

        var filterTaskStateLoading: Boolean,
        var filterTaskStateLoaded: Boolean,
        var filterTaskRunning: Boolean,
        var filterTaskId: TaskId?,
        var filterTaskProgress: TaskProgress?,
        var filterTaskState: TaskState?,
        var filterTaskOutput: String?,

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

    private var filterProgressDebounce: FunctionWithDebounce = lodash.debounce({
        updateFilterTaskProgress()
    }, 5_000)


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        clientState = null

        error = null

        initialTableSummaryLoading = false
        tableSummaryTaskRunning = false
        tableSummaryTaskId = null
        tableSummaryTaskProgress = null
        tableSummaryTaskState = null
        tableSummary = null

        filterTaskStateLoading = false
        filterTaskStateLoaded = false
        filterTaskRunning = false
        filterTaskId = null
        filterTaskProgress = null
        filterTaskOutput = null

        inputChanged = false
    }


    override fun componentDidMount() {
        async {
            ClientContext.sessionGlobal.observe(this)
        }
    }


    override fun componentWillUnmount() {
        ClientContext.sessionGlobal.unobserve(this)
        tableSummaryProgressDebounce.cancel()
        filterProgressDebounce.cancel()
    }


    override fun componentDidUpdate(
            prevProps: Props,
            prevState: State,
            snapshot: Any
    ) {
//        console.log("%$%$%%$ componentDidUpdate")

        val clientState = state.clientState ?: return
        val mainLocation = mainLocation(clientState) ?: return

        val prevClientState = prevState.clientState
        if (prevClientState != null &&
                inputPath(clientState, mainLocation) != inputPath(prevClientState, mainLocation)) {
            setState {
                inputChanged = true
            }
        }

        if (state.error != null) {
//            console.log("error: ${state.error}")
            return
        }

        if (state.initialTableSummaryLoading && ! prevState.initialTableSummaryLoading) {
            getInitialTableSummary()
            return
        }

//        console.log("%$%$%$$% ", state.tableSummary, state.initialTableSummaryLoading)
        if (state.tableSummary == null && ! state.initialTableSummaryLoading) {
//            console.log("^^^^ initialTableSummaryLoading = true")
            setState {
                initialTableSummaryLoading = true
            }
            return
        }

        if (! state.initialTableSummaryLoading &&
                ! state.tableSummaryTaskRunning &&
                state.tableSummary != null)
        {
            if (state.filterTaskStateLoading && ! prevState.filterTaskStateLoading) {
                async {
                    lookupRunningTableFilterTask()
                }
                return
            }

            if (state.filterTaskProgress == null &&
                    ! state.filterTaskStateLoading &&
                    ! state.filterTaskStateLoaded)
            {
                setState {
                    filterTaskStateLoading = true
                }
                return
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
//        console.log("^^^^^^^^^^ getInitialTableSummary")
        val mainLocation = mainLocation()!!

        async {
            val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(mainLocation)
            if (activeTasks.isNotEmpty()) {
                check(activeTasks.size == 1)
                val taskId = activeTasks.first()
                val model = ClientContext.clientRestTaskRepository.query(taskId)!!
                val action = model.request.parameters.get(FilterConventions.actionParameter)
                if (action == FilterConventions.actionSummaryTask) {
                    val result =
                        model.finalResult ?: model.partialResult!!

                    when (result) {
                        is ExecutionSuccess -> {
                            @Suppress("UNCHECKED_CAST")
                            val resultValue = result.value.get() as Map<String, Map<String, Any>>
                            val tableSummary = TableSummary.fromCollection(resultValue)

                            @Suppress("UNCHECKED_CAST")
                            val resultDetail = result.detail.get() as Map<String, String>?
                            val tableSummaryProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                            setState {
                                this.tableSummaryTaskProgress = tableSummaryProgress
                                this.tableSummary = tableSummary
                            }
                        }

                        is ExecutionFailure -> {
                            setState {
                                error = result.errorMessage
                            }
                        }
                    }

                    val isDone = model.finalResult != null

                    setState {
                        tableSummaryTaskRunning = ! isDone
                        tableSummaryTaskId = model.taskId
                        initialTableSummaryLoading = false
                        tableSummaryTaskState = model.state
                    }

                    if (! isDone) {
//                        console.log("^^^^^^^ apply tableSummaryProgressDebounce")
                        tableSummaryProgressDebounce.apply()
                    }

                    return@async
                }
            }

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
                    val summaryProgress = TaskProgress.fromCollection(resultDetail)

                    setState {
                        this.tableSummary = tableSummary
                        this.tableSummaryTaskProgress = summaryProgress
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


    private fun submitOrResumeTableSummaryTask() {
//        console.log("^^^^^^^^^^ submitOrResumeTableSummaryTask")
        val mainLocation = mainLocation()!!

        async {
            val taskModel: TaskModel =
                if (state.tableSummaryTaskId != null) {
                    ClientContext.clientRestTaskRepository.query(state.tableSummaryTaskId!!)!!
                }
                else {
                    val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(mainLocation)

                    if (activeTasks.isNotEmpty()) {
                        check(activeTasks.size == 1)
                        val taskId = activeTasks.first()
                        val model = ClientContext.clientRestTaskRepository.query(taskId)!!
                        val action = model.request.parameters.get(FilterConventions.actionParameter)
                        if (action != FilterConventions.actionSummaryTask) {
                            return@async
                        }
                        model
                    }
                    else {
                        ClientContext.clientRestTaskRepository.submit(
                            mainLocation,
                            DetachedRequest(
                                RequestParams.of(
                                    FilterConventions.actionParameter to FilterConventions.actionSummaryTask),
                                null))
                    }
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
                    val tableSummaryProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

//                    console.log("&&%&%&%&&^% tableSummary: $tableSummary")
                    setState {
                        this.tableSummaryTaskProgress = tableSummaryProgress
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
                tableSummaryTaskRunning = ! isDone
                tableSummaryTaskId = taskModel.taskId
                tableSummaryTaskState = taskModel.state
            }

//            console.log("^^^^ about to tableSummaryProgressDebounce - $isDone")
            if (! isDone) {
//                console.log("^^^^^^^ apply tableSummaryProgressDebounce")
                tableSummaryProgressDebounce.apply()
            }
        }
    }



    private fun cancelTableSummaryTask() {
        if (! state.tableSummaryTaskRunning) {
            return
        }

        val taskId = state.tableSummaryTaskId
            ?: return

        async {
            setState {
//                filterTaskRunning = false
                tableSummaryTaskRunning = false
            }

            val taskModel = ClientContext.clientRestTaskRepository.cancel(taskId)
                ?: return@async

            setState {
                tableSummaryTaskState = TaskState.Cancelled
            }

            when (
                val result = taskModel.finalResult!!
            ) {
                is ExecutionSuccess -> {
//                    console.log("%%%%%% result.value: ${result.value}")

                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as Map<String, Map<String, Any>>
                    val tableSummary = TableSummary.fromCollection(resultValue)

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val tableSummaryProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                    setState {
                        this.tableSummaryTaskProgress = tableSummaryProgress
                        this.tableSummary = tableSummary
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


    private fun updateTableSummaryProgress() {
//        console.log("&&&&& updateTableSummaryProgress - ${state.tableSummaryTaskRunning} - ${state.tableSummaryTaskId}")

        if (! state.tableSummaryTaskRunning) {
            return
        }

        val taskId = state.tableSummaryTaskId
            ?: return

        async {
            val taskModel: TaskModel = ClientContext.clientRestTaskRepository.query(taskId)!!
            check(taskModel.request.parameters.get(FilterConventions.actionParameter) ==
                    FilterConventions.actionSummaryTask)

            val result =
                taskModel.finalResult ?: taskModel.partialResult!!

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as Map<String, Map<String, Any>>
                    val tableSummary = TableSummary.fromCollection(resultValue)

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val tableSummaryProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                    setState {
                        if (this.tableSummaryTaskProgress != tableSummaryProgress) {
                            this.tableSummaryTaskProgress = tableSummaryProgress
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
                tableSummaryTaskRunning = ! isDone
                tableSummaryTaskId = taskId
            }

            if (! isDone) {
                tableSummaryProgressDebounce.apply()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun lookupRunningTableFilterTask() {
//        console.log("^^^^^^^ lookupTableFilterTask")

        val mainLocation = mainLocation()!!

        val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(mainLocation)
        if (activeTasks.isEmpty()) {
            setState {
                filterTaskStateLoading = false
                filterTaskStateLoaded = true
            }
            return
        }

        check(activeTasks.size == 1)
        val taskId = activeTasks.first()
        val taskModel = ClientContext.clientRestTaskRepository.query(taskId)!!
        val action = taskModel.request.parameters.get(FilterConventions.actionParameter)
        if (action != FilterConventions.actionFilterTask) {
            setState {
                filterTaskStateLoading = false
                filterTaskStateLoaded = true
            }
            return
        }
        else {
            setState {
                filterTaskState = taskModel.state
            }
        }

        val result =
            taskModel.finalResult ?: taskModel.partialResult!!

        when (result) {
            is ExecutionSuccess -> {
                @Suppress("UNCHECKED_CAST")
                val resultValue = result.value.get() as String

                @Suppress("UNCHECKED_CAST")
                val resultDetail = result.detail.get() as Map<String, String>?
                val filterTaskProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                setState {
                    this.filterTaskProgress = filterTaskProgress
                    this.filterTaskOutput = resultValue
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
            filterTaskRunning = ! isDone
            filterTaskStateLoading = false
            filterTaskStateLoaded = true
            filterTaskId = taskId
        }

        if (! isDone) {
            filterProgressDebounce.apply()
        }
    }


    private fun submitOrResumeTableFilterTask() {
        val mainLocation = mainLocation()!!

        async {
            val activeTasks = ClientContext.clientRestTaskRepository.lookupActive(mainLocation)

            val taskModel: TaskModel =
                if (activeTasks.isNotEmpty()) {
                    check(activeTasks.size == 1)
                    val taskId = activeTasks.first()
                    val model = ClientContext.clientRestTaskRepository.query(taskId)!!
                    val action = model.request.parameters.get(FilterConventions.actionParameter)
                    if (action != FilterConventions.actionFilterTask) {
                        return@async
                    }
                    model
                }
                else {
                    ClientContext.clientRestTaskRepository.submit(
                        mainLocation,
                        DetachedRequest(
                            RequestParams.of(
                                FilterConventions.actionParameter to FilterConventions.actionFilterTask),
                            null))
                }

            val result =
                taskModel.finalResult ?: taskModel.partialResult!!

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as String

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val filterTaskProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                    setState {
                        this.filterTaskProgress = filterTaskProgress
                        this.filterTaskOutput = resultValue
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
                filterTaskRunning = ! isDone
                filterTaskId = taskModel.taskId
                filterTaskState = taskModel.state
            }

//            console.log("^^^^ about to filterProgressDebounce - $isDone")
            if (! isDone) {
                filterProgressDebounce.apply()
            }
        }
    }


    private fun cancelFilterTask() {
        if (! state.filterTaskRunning) {
            return
        }

        val taskId = state.filterTaskId
            ?: return

        async {
            setState {
                filterTaskRunning = false
            }

            val taskModel = ClientContext.clientRestTaskRepository.cancel(taskId)
                ?: return@async

            setState {
                filterTaskState = TaskState.Cancelled
            }

            when (
                val result = taskModel.finalResult!!
                ) {
                is ExecutionSuccess -> {
//                    console.log("%%%%%% result.value: ${result.value}")

                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as String

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val taskProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                    setState {
                        this.filterTaskProgress = taskProgress
                        this.filterTaskOutput = resultValue
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


    private fun updateFilterTaskProgress() {
//        console.log("&&&&& updateFilterTaskProgress - ${state.filterTaskRunning} - ${state.filterTaskId}")

        if (! state.filterTaskRunning) {
            return
        }

        val taskId = state.filterTaskId
            ?: return

        async {
            val taskModel: TaskModel = ClientContext.clientRestTaskRepository.query(taskId)!!
            check(taskModel.request.parameters.get(FilterConventions.actionParameter) ==
                    FilterConventions.actionFilterTask)

            val result =
                taskModel.finalResult ?: taskModel.partialResult!!

            when (result) {
                is ExecutionSuccess -> {
                    @Suppress("UNCHECKED_CAST")
                    val resultValue = result.value.get() as String

                    @Suppress("UNCHECKED_CAST")
                    val resultDetail = result.detail.get() as Map<String, String>?
                    val filterTaskProgress = resultDetail?.let { TaskProgress.fromCollection(it) }

                    setState {
                        if (this.filterTaskProgress != filterTaskProgress) {
                            this.filterTaskProgress = filterTaskProgress
                        }

                        if (this.filterTaskOutput != resultValue) {
                            this.filterTaskOutput = resultValue
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
                filterTaskRunning = ! isDone
            }

            if (! isDone) {
                filterProgressDebounce.apply()
            }
            else {

            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun refresh() {
        setState {
            error = null

//            fileListingLoading = false
//            fileListing = null

//            columnListingLoading = false
//            columnListing = null

//            filterTaskRunning = false
//            filterTaskOutput = null

            inputChanged = false
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
                this.taskRunning = state.tableSummaryTaskRunning || state.filterTaskRunning
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
                this.filterRunning = state.filterTaskRunning
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
                this.summaryTaskRunning = state.tableSummaryTaskRunning
                this.summaryEmpty = (state.tableSummary ?: TableSummary.empty).isEmpty()
                this.summaryProgress = state.tableSummaryTaskProgress
                this.summaryState = state.tableSummaryTaskState
                this.tableSummary = state.tableSummary

                this.filterTaskStateLoading = state.filterTaskStateLoading
                this.filterTaskRunning = state.filterTaskRunning
                this.filterTaskProgress = state.filterTaskProgress
                this.filterTaskState = state.filterTaskState
                this.filterTaskOutput = state.filterTaskOutput
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

                    summaryDone = (state.tableSummaryTaskProgress?.remainingFiles?.isEmpty() ?: false) ||
                            ! (state.tableSummary?.isEmpty() ?: true)

                    summaryInitialRunning = state.initialTableSummaryLoading
                    summaryTaskRunning = state.tableSummaryTaskRunning

                    filterDone = state.filterTaskProgress?.remainingFiles?.isEmpty() ?: false
                    filterRunning = state.filterTaskRunning

                    onSummaryTask = {
                        submitOrResumeTableSummaryTask()
                    }

                    onSummaryCancel = {
                        cancelTableSummaryTask()
                    }

                    onFilterTask = {
                        submitOrResumeTableFilterTask()
                    }

                    onFilterCancel = {
                        cancelFilterTask()
                    }
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
}