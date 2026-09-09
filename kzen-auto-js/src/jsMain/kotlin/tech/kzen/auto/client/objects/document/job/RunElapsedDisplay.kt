package tech.kzen.auto.client.objects.document.job

import emotion.react.css
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.common.paradigm.logic.RunTiming
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.setState
import kotlin.time.TimeSource
import web.cssom.*

external interface RunElapsedDisplayProps: Props { var timing: RunTiming? }
external interface RunElapsedDisplayState: State { var elapsed: Long }

/** Only this small component ticks; the worker stage and trace fetching remain version-driven. */
class RunElapsedDisplay(props: RunElapsedDisplayProps): RPureComponent<RunElapsedDisplayProps, RunElapsedDisplayState>(props) {
    private val scope = MainScope()
    private var anchor = TimeSource.Monotonic.markNow()
    private var base = props.timing?.elapsedMillis ?: 0

    override fun RunElapsedDisplayState.init(props: RunElapsedDisplayProps) { elapsed = props.timing?.elapsedMillis ?: 0 }

    override fun componentDidMount() {
        scope.launch {
            while (isActive) {
                delay(tickMillis)
                if (props.timing?.settled == false) setState { elapsed = base + anchor.elapsedNow().inWholeMilliseconds }
            }
        }
    }

    override fun componentDidUpdate(prevProps: RunElapsedDisplayProps, prevState: RunElapsedDisplayState, snapshot: Any) {
        if (prevProps.timing == props.timing) return
        anchor = TimeSource.Monotonic.markNow()
        base = props.timing?.elapsedMillis ?: 0
        setState { elapsed = base }
    }

    override fun componentWillUnmount() { scope.cancel() }

    override fun ChildrenBuilder.render() {
        val timing = props.timing ?: return
        val seconds = state.elapsed / tickMillis
        val hours = seconds / secondsPerHour
        val minutes = seconds / secondsPerMinute % secondsPerMinute
        val remainingSeconds = seconds % secondsPerMinute
        div {
            css { color = Color("#596579"); fontSize = 0.9.em; marginBottom = 0.75.em }
            title = "Total time including pauses and waits, until the entire run finishes"
            +(if (timing.settled) "Run duration: " else "Run elapsed: ")
            +(if (hours > 0) "${hours}h " else "")
            +(if (minutes > 0 || hours > 0) "${minutes}m " else "")
            +"${remainingSeconds}s"
        }
    }

    companion object {
        private const val tickMillis = 1000L
        private const val secondsPerMinute = 60
        private const val secondsPerHour = 3600
    }
}
