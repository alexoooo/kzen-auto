package tech.kzen.auto.client.objects.document.custom.view.obj

import emotion.react.css
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
import tech.kzen.lib.common.exec.ExecutionFailure
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.location.ObjectLocation
import web.cssom.FontStyle
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
class CustomObjectDetachedRunner {
    private val observers = mutableSetOf<() -> Unit>()

    var running: Boolean = false
        private set

    var lastResult: ExecutionResult? = null
        private set


    fun observe(observer: () -> Unit) {
        observers += observer
    }


    fun unobserve(observer: () -> Unit) {
        observers.remove(observer)
    }


    fun run(objectLocation: ObjectLocation) {
        if (running) {
            return
        }
        running = true
        notifyObservers()
        async {
            val result = ClientContext.restClient.performDetached(objectLocation)
            running = false
            lastResult = result
            notifyObservers()
        }
    }


    private fun notifyObservers() {
        observers.forEach { it() }
    }
}


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectDetachedHeaderProps: Props {
    var runner: CustomObjectDetachedRunner
    var objectLocation: ObjectLocation
}


external interface CustomObjectDetachedHeaderState: State {
    var running: Boolean
}


@Suppress("unused")
class CustomObjectDetachedHeader(
    props: CustomObjectDetachedHeaderProps
):
    RPureComponent<CustomObjectDetachedHeaderProps, CustomObjectDetachedHeaderState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectDetachedHeaderState.init(props: CustomObjectDetachedHeaderProps) {
        running = props.runner.running
    }


    override fun componentDidMount() {
        props.runner.observe(::onRunnerChanged)
    }


    override fun componentWillUnmount() {
        props.runner.unobserve(::onRunnerChanged)
    }


    private fun onRunnerChanged() {
        val newRunning = props.runner.running
        setState {
            running = newRunning
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        IconButton {
            title = "Run"
            size = Size.small
            disabled = state.running

            sx {
                marginLeft = 0.5.em
            }

            onClick = { props.runner.run(props.objectLocation) }

            iconByName("PlayArrow") {}
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
external interface CustomObjectDetachedBodyProps: Props {
    var runner: CustomObjectDetachedRunner
}


external interface CustomObjectDetachedBodyState: State {
    var running: Boolean
    var lastResult: ExecutionResult?
}


@Suppress("unused")
class CustomObjectDetachedBody(
    props: CustomObjectDetachedBodyProps
):
    RPureComponent<CustomObjectDetachedBodyProps, CustomObjectDetachedBodyState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    override fun CustomObjectDetachedBodyState.init(props: CustomObjectDetachedBodyProps) {
        running = props.runner.running
        lastResult = props.runner.lastResult
    }


    override fun componentDidMount() {
        props.runner.observe(::onRunnerChanged)
    }


    override fun componentWillUnmount() {
        props.runner.unobserve(::onRunnerChanged)
    }


    private fun onRunnerChanged() {
        val newRunning = props.runner.running
        val newLastResult = props.runner.lastResult
        setState {
            running = newRunning
            lastResult = newLastResult
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        if (state.running) {
            div {
                css {
                    marginBottom = 0.75.em
                    fontStyle = FontStyle.italic
                    color = CustomTheme.mutedText
                }
                +"Running…"
            }
            return
        }

        when (val result = state.lastResult) {
            null -> {}

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
        }
    }
}
