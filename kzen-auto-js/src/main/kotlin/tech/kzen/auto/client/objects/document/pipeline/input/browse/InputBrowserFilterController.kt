package tech.kzen.auto.client.objects.document.pipeline.input.browse

import kotlinx.css.em
import kotlinx.css.width
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import tech.kzen.auto.client.objects.document.pipeline.input.model.PipelineInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialInputAdornment
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.material.SearchIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec


class InputBrowserFilterController(
    props: Props
):
    RPureComponent<InputBrowserFilterController.Props, InputBrowserFilterController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var spec: InputBrowserSpec
        var inputStore: PipelineInputStore

//        var reportState: ReportState
//        var dispatcher: ReportDispatcher
//        var editDisabled: Boolean
    }


    interface State: RState {
        var filterText: String
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        filterText = props.spec.filter
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
        if (props.spec.filter == state.filterText) {
            return
        }

        props.inputStore.browser.browserFilterUpdateAsync(state.filterText)
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
                    width = 20.em
                }

                size = "small"

                InputProps = object : RProps {
                    @Suppress("unused")
                    var startAdornment = child(MaterialInputAdornment::class) {
                        attrs {
                            position = "start"
                        }
                        child(SearchIcon::class) {}
                    }
                }

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                value = state.filterText
//                disabled = props.editDisabled
                onKeyDown = ::handleEnter
            }
        }
    }
}