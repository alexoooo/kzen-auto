package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.*
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.*
import tech.kzen.lib.common.model.locate.ObjectLocation


class FilterRun(
    props: Props
):
    RPureComponent<FilterRun.Props, FilterRun.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState,

        var summaryDone: Boolean,
        var summaryInitialRunning: Boolean,
        var summaryTaskRunning: Boolean,
//        var summaryState: TaskState?,

        var filterDone: Boolean,
        var filterRunning: Boolean,

        var onSummaryTask: () -> Unit,
        var onSummaryCancel: () -> Unit,
        var onFilterTask: () -> Unit,
        var onFilterCancel: () -> Unit
    ): RProps


    class State(
//        var columnListingLoading: Boolean,
//        var columnListing: List<String>?,
        var error: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
//        val runnable = ! props.summaryDone || ! props.filterDone

        child(MaterialFab::class) {
            attrs {
                title = when {
                    props.summaryInitialRunning ->
                        "Loading"

                    props.summaryTaskRunning ->
                        "Pause index"

                    props.filterRunning ->
                        "Stop filtering"

                    ! props.summaryDone ->
                        "Index column values"

//                    state.writingOutput ->
//                        "Running..."

                    else ->
                        "Filter"
                }

                style = reactStyle {
                    backgroundColor =
                        if (props.summaryInitialRunning ||
                                props.summaryTaskRunning ||
                                props.filterRunning
                        ) {
                            Color.white
                        }
                        else {
                            Color.gold
                        }

                    width = 5.em
                    height = 5.em
                }

                onClick = {
                    if (props.summaryTaskRunning) {
                        props.onSummaryCancel()
                    }
                    else if (! props.summaryDone) {
                        props.onSummaryTask()
                    }
                    else if (props.filterRunning) {
                        props.onFilterCancel()
                    }
                    else if (! props.filterDone) {
                        props.onFilterTask()
                    }
                }
            }

            if (props.summaryInitialRunning) {
                child(MaterialCircularProgress::class) {}
            }
            else if (props.summaryTaskRunning) {
                renderProgressWithPause()
            }
            else if (! props.summaryDone) {
                child(MenuBookIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
            else if (props.filterRunning) {
                renderProgressWithStop()
            }
            else if (! props.filterDone) {
                child(PlayArrowIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }
                    }
                }
            }
            else {
                +"X"
            }
        }
    }


    private fun RBuilder.renderProgressWithPause() {
        child(MaterialCircularProgress::class) {}

        child(PauseIcon::class) {
            attrs {
                style = reactStyle {
                    fontSize = 3.em
                    margin = "auto"
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    bottom = 0.px
                    right = 0.px
                }
            }
        }
    }


    private fun RBuilder.renderProgressWithStop() {
        child(MaterialCircularProgress::class) {}

        child(StopIcon::class) {
            attrs {
                style = reactStyle {
                    fontSize = 3.em
                    margin = "auto"
                    position = Position.absolute
                    top = 0.px
                    left = 0.px
                    bottom = 0.px
                    right = 0.px
                }
            }
        }
    }
}