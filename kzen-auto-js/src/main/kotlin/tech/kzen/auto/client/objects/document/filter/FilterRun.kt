package tech.kzen.auto.client.objects.document.filter

import kotlinx.css.em
import kotlinx.css.height
import kotlinx.css.width
import react.RBuilder
import react.RProps
import react.RPureComponent
import react.RState
import tech.kzen.auto.client.service.global.SessionState
import tech.kzen.auto.client.wrap.MaterialFab
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.lib.common.model.locate.ObjectLocation


class FilterRun(
    props: Props
):
    RPureComponent<FilterRun.Props, FilterRun.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    class Props(
        var mainLocation: ObjectLocation,
        var clientState: SessionState
    ): RProps


    class State(
//        var columnListingLoading: Boolean,
//        var columnListing: List<String>?,
        var error: String?
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialFab::class) {
            attrs {
                title = when {
//                    state.writingOutput ->
//                        "Running..."

                    else ->
                        "Run"
                }

                style = reactStyle {
//                    backgroundColor =
//                        if (state.writingOutput) {
//                            Color.white
//                        }
//                        else {
//                            Color.gold
//                        }

                    width = 5.em
                    height = 5.em
                }
            }
            +"foo"

//            if (state.writingOutput) {
//                child(MaterialCircularProgress::class) {}
//            }
//            else if (state.inputChanged) {
//                child(RefreshIcon::class) {
//                    attrs {
//                        style = reactStyle {
//                            fontSize = 3.em
//                        }
//
//                        onClick = {
//                            refresh()
//                        }
//                    }
//                }
//            }
//            else {
//                child(PlayArrowIcon::class) {
//                    attrs {
//                        style = reactStyle {
//                            fontSize = 3.em
//                        }
//
//                        onClick = {
//                            applyFilter(mainLocation)
//                        }
//                    }
//                }
//            }
        }
    }
}