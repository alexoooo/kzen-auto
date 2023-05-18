package tech.kzen.auto.client.objects.document.report.input.browse

import emotion.react.css
import js.core.jso
import mui.material.InputAdornment
import mui.material.InputAdornmentPosition
import mui.material.Size
import mui.material.TextField
import react.*
import react.dom.onChange
import tech.kzen.auto.client.objects.document.report.input.model.ReportInputStore
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.*
import tech.kzen.auto.client.wrap.material.SearchIcon
import tech.kzen.auto.common.objects.document.report.spec.input.InputBrowserSpec
import web.cssom.em
import web.html.HTMLDivElement
import web.html.HTMLInputElement


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


    private fun handleEnter(event: react.dom.events.KeyboardEvent<HTMLDivElement>) {
        ClientInputUtils.handleEnter(event) {
            submitDebounce.cancel()
            submitEdit()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        TextField {
            size = Size.small
            css {
                width = 20.em
            }


//            val adornment = InputAdornment.create {
//                position = mui.material.InputAdornmentPosition.start
//                +"kg"
//            }

            InputProps = jso {
                startAdornment = InputAdornment.create {
                    position = InputAdornmentPosition.start
                    SearchIcon::class.react {}
                }
            }

            onChange = {
                val target = it.target as HTMLInputElement
                onValueChange(target.value)
            }

            value = state.filterText
//                disabled = props.editDisabled

            onKeyDown = { e: react.dom.events.KeyboardEvent<HTMLDivElement> ->
                handleEnter(e)
            }
        }
    }
}