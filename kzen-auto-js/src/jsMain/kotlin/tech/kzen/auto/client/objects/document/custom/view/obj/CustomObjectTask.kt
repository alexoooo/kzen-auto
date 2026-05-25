package tech.kzen.auto.client.objects.document.custom.view.obj

import emotion.react.css
import kotlinx.coroutines.delay
import mui.material.IconButton
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.custom.CustomTheme
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.material.iconByName
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.paradigm.task.model.TaskModel
import tech.kzen.auto.common.paradigm.task.model.TaskState
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.FontStyle
import web.cssom.em
import kotlin.time.Duration.Companion.milliseconds


//---------------------------------------------------------------------------------------------------------------------
class CustomObjectTaskRunner {
    private val taskPollIntervalMillis = 1_000.milliseconds
    private val observers = mutableSetOf<() -> Unit>()

    var submitting: Boolean = false
        private set

    var taskModel: TaskModel? = null
        private set


    fun observe(observer: () -> Unit) {
        observers += observer
    }


    fun unobserve(observer: () -> Unit) {
        observers.remove(observer)
    }


    fun run(objectLocation: ObjectLocation) {
        if (submitting || taskModel?.state == TaskState.Running) {
            return
        }
        submitting = true
        notifyObservers()
        async {
            val request = ExecutionRequest(RequestParams.empty, null)
            val submitted = ClientContext.clientRestTaskRepository.submit(objectLocation, request)
            submitting = false
            taskModel = submitted
            notifyObservers()
            pollLoop()
        }
    }


    fun cancel() {
        val current = taskModel ?: return
        if (current.state != TaskState.Running) {
            return
        }
        async {
            val cancelled = ClientContext.clientRestTaskRepository.cancel(current.taskId)
            if (cancelled != null) {
                taskModel = cancelled
                notifyObservers()
            }
        }
    }


    private suspend fun pollLoop() {
        while (isActiveState(taskModel?.state)) {
            delay(taskPollIntervalMillis)
            val current = taskModel ?: break
            if (!isActiveState(current.state)) {
                break
            }
            val updated = ClientContext.clientRestTaskRepository.query(current.taskId) ?: break
            taskModel = updated
            notifyObservers()
        }
    }


    private fun isActiveState(state: TaskState?): Boolean {
        return state == TaskState.Running || state == TaskState.CancelRequested
    }


    private fun notifyObservers() {
        observers.forEach { it() }
    }
}


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectTaskHeaderProps: Props {
    var runner: CustomObjectTaskRunner
    var objectLocation: ObjectLocation
}


external interface CustomObjectTaskHeaderState: State {
    var submitting: Boolean
    var taskState: TaskState?
}


@Suppress("unused")
class CustomObjectTaskHeader(
    props: CustomObjectTaskHeaderProps
):
    RPureComponent<CustomObjectTaskHeaderProps, CustomObjectTaskHeaderState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectTaskHeaderState.init(props: CustomObjectTaskHeaderProps) {
        submitting = props.runner.submitting
        taskState = props.runner.taskModel?.state
    }


    override fun componentDidMount() {
        props.runner.observe(::onRunnerChanged)
    }


    override fun componentWillUnmount() {
        props.runner.unobserve(::onRunnerChanged)
    }


    private fun onRunnerChanged() {
        val newSubmitting = props.runner.submitting
        val newTaskState = props.runner.taskModel?.state
        setState {
            submitting = newSubmitting
            taskState = newTaskState
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        when (state.taskState) {
            TaskState.Running -> {
                IconButton {
                    title = "Stop"
                    size = Size.small

                    sx {
                        marginLeft = 0.5.em
                        color = CustomTheme.danger
                    }

                    onClick = { props.runner.cancel() }

                    iconByName("Stop") {}
                }
            }

            TaskState.CancelRequested -> {
                IconButton {
                    title = "Stopping…"
                    size = Size.small
                    disabled = true

                    sx {
                        marginLeft = 0.5.em
                    }

                    iconByName("Stop") {}
                }
            }

            else -> {
                IconButton {
                    title = "Run"
                    size = Size.small
                    disabled = state.submitting

                    sx {
                        marginLeft = 0.5.em
                    }

                    onClick = { props.runner.run(props.objectLocation) }

                    iconByName("PlayArrow") {}
                }
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectTaskBodyProps: Props {
    var runner: CustomObjectTaskRunner
}


external interface CustomObjectTaskBodyState: State {
    var submitting: Boolean
    var taskModel: TaskModel?
}


@Suppress("unused")
class CustomObjectTaskBody(
    props: CustomObjectTaskBodyProps
):
    RPureComponent<CustomObjectTaskBodyProps, CustomObjectTaskBodyState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectTaskBodyState.init(props: CustomObjectTaskBodyProps) {
        submitting = props.runner.submitting
        taskModel = props.runner.taskModel
    }


    override fun componentDidMount() {
        props.runner.observe(::onRunnerChanged)
    }


    override fun componentWillUnmount() {
        props.runner.unobserve(::onRunnerChanged)
    }


    private fun onRunnerChanged() {
        val newSubmitting = props.runner.submitting
        val newTaskModel = props.runner.taskModel
        setState {
            submitting = newSubmitting
            taskModel = newTaskModel
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val model = state.taskModel

        if (model == null) {
            if (state.submitting) {
                renderPending("Submitting…")
            }
            return
        }

        when (model.state) {
            TaskState.Running -> {
                renderPending("Running…")
                renderPartialValue(model.partialResult)
            }

            TaskState.CancelRequested -> {
                renderPending("Stopping…")
                renderPartialValue(model.partialResult)
            }

            TaskState.Cancelled -> {
                renderPending("Cancelled")
                val salvaged = (model.finalResult as? ExecutionSuccess) ?: model.partialResult
                renderPartialValue(salvaged)
            }

            TaskState.FinishedOrFailed -> {
                when (val result = model.finalResult) {
                    is ExecutionSuccess -> {
                        div {
                            css {
                                marginBottom = 0.75.em
                                color = CustomTheme.successText
                            }
                            +"✓ Result: ${result.value}"
                        }
                    }

                    is ExecutionFailure -> {
                        div {
                            css {
                                marginBottom = 0.75.em
                                color = CustomTheme.danger
                            }
                            +"✗ ${result.errorMessage}"
                        }
                    }

                    null ->
                        error("FinishedOrFailed task has null finalResult: ${model.taskId}")
                }
            }
        }
    }


    private fun ChildrenBuilder.renderPending(text: String) {
        div {
            css {
                marginBottom = 0.75.em
                fontStyle = FontStyle.italic
                color = CustomTheme.mutedText
            }
            +text
        }
    }


    private fun ChildrenBuilder.renderPartialValue(partial: ExecutionSuccess?) {
        val value = partial?.value?.get() ?: return
        div {
            css {
                marginBottom = 0.75.em
                fontSize = 0.9.em
                color = CustomTheme.mutedText
            }
            +value.toString()
        }
    }
}
