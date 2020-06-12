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
        var summaryRunning: Boolean,
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
                    props.summaryRunning ->
                        "Pause index"

                    ! props.summaryDone ->
                        "Index column values"

//                    state.writingOutput ->
//                        "Running..."

                    else ->
                        "Filter"
                }

                style = reactStyle {
                    backgroundColor =
                        if (props.summaryRunning || props.filterRunning) {
                            Color.white
                        }
                        else {
                            Color.gold
                        }

                    width = 5.em
                    height = 5.em
                }

                onClick = {
                    if (props.summaryRunning) {
                        props.onSummaryCancel()
                    }
                    else if (! props.summaryDone) {
                        props.onSummaryTask()
                    }
                    else if (props.filterRunning) {
                        // TODO
                    }
                    else if (! props.filterDone) {
                        props.onFilterTask()
                    }
                }
            }

            if (props.summaryRunning) {
                child(MaterialCircularProgress::class) {}
                +"X"
            }
            else if (! props.summaryDone) {
                child(MenuBookIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }

//                        onClick = {}
                    }
                }
            }
            else if (props.filterRunning) {
                child(MaterialCircularProgress::class) {}
            }
            else if (! props.filterDone) {
                child(PlayArrowIcon::class) {
                    attrs {
                        style = reactStyle {
                            fontSize = 3.em
                        }

//                        onClick = {}
                    }
                }
            }
            else {
                +"X"
            }
        }
    }
}