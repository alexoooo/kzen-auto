package tech.kzen.auto.client.objects.document.report.input.browse

import kotlinx.css.em
import kotlinx.css.width
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import tech.kzen.auto.client.objects.document.report.state.InputsBrowserFilterRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialInputAdornment
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.material.SearchIcon
import tech.kzen.auto.client.wrap.reactStyle


class InputBrowserFilter(
    props: Props
):
    RPureComponent<InputBrowserFilter.Props, InputBrowserFilter.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: react.Props {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: react.State {
        var filterText: String
//        var browserOpen: Boolean
//        var selected: PersistentSet<String>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        filterText = props.reportState.inputSpec().browser.filter
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var submitDebounce: FunctionWithDebounce = lodash.debounce({
        async {
            submitEdit()
        }
    }, 1000)


    //-----------------------------------------------------------------------------------------------------------------
    private fun onValueChange(newValue: String) {
        setState {
            filterText = newValue
        }
        submitDebounce.apply()
    }


    private fun submitEdit() {
        if (props.reportState.inputSpec().browser.filter == state.filterText) {
            return
        }

        props.dispatcher.dispatchAsync(InputsBrowserFilterRequest(state.filterText))
    }


    private fun handleEnter(event: KeyboardEvent) {
        ClientInputUtils.handleEnter(event) {
            submitDebounce.cancel()
            submitEdit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        child(MaterialTextField::class) {
            attrs {
                style = reactStyle {
//                    float = Float.right
                    width = 20.em
                }

                size = "small"

                InputProps = object : react.Props {
                    @Suppress("unused")
                    var startAdornment = buildElement {
                        child(MaterialInputAdornment::class) {
                            attrs {
                                position = "start"
                            }
                            child(SearchIcon::class) {}
                        }
                    }
                }

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                value = state.filterText
                disabled = props.editDisabled
                onKeyDown = ::handleEnter
            }
        }
    }
}