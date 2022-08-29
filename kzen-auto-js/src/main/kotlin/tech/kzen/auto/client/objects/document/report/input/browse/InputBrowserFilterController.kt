package tech.kzen.auto.client.objects.document.report.input.browse

import kotlinx.css.em
import kotlinx.css.width
import kotlinx.js.jso
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialInputAdornment
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.material.SearchIcon
import tech.kzen.auto.client.wrap.reactStyle
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec


//---------------------------------------------------------------------------------------------------------------------
external interface InputBrowserFilterControllerProps: Props {
    var spec: InputBrowserSpec
    var inputStore: ReportInputStore
}


external interface InputBrowserFilterControllerState: State {
    var filterText: String
}


//---------------------------------------------------------------------------------------------------------------------
class InputBrowserFilterController(
    props: InputBrowserFilterControllerProps
):
    RPureComponent<InputBrowserFilterControllerProps, InputBrowserFilterControllerState>(props)
{


    //-----------------------------------------------------------------------------------------------------------------
    override fun InputBrowserFilterControllerState.init(props: InputBrowserFilterControllerProps) {
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

                InputProps = jso {
                    @Suppress("UNUSED_VARIABLE")
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
//                disabled = props.editDisabled
                onKeyDown = ::handleEnter
            }
        }
    }
}