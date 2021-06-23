package tech.kzen.auto.client.objects.document.report.output

import kotlinx.css.em
import kotlinx.css.marginTop
import kotlinx.css.pct
import kotlinx.css.width
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.*
import tech.kzen.auto.client.objects.document.report.state.ExportPathRequest
import tech.kzen.auto.client.objects.document.report.state.ReportDispatcher
import tech.kzen.auto.client.objects.document.report.state.ReportState
import tech.kzen.auto.client.util.ClientInputUtils
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.FunctionWithDebounce
import tech.kzen.auto.client.wrap.iconify.iconify
import tech.kzen.auto.client.wrap.iconify.vaadinIconFile
import tech.kzen.auto.client.wrap.lodash
import tech.kzen.auto.client.wrap.material.MaterialInputAdornment
import tech.kzen.auto.client.wrap.material.MaterialTextField
import tech.kzen.auto.client.wrap.reactStyle


class OutputExportPath(
    props: Props
):
    RPureComponent<OutputExportPath.Props, OutputExportPath.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    interface Props: RProps {
        var reportState: ReportState
        var dispatcher: ReportDispatcher
        var editDisabled: Boolean
    }


    interface State: RState {
        var pathText: String
//        var browserOpen: Boolean
//        var selected: PersistentSet<String>
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun State.init(props: Props) {
        pathText = props.reportState.outputSpec().export.pathPattern
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
            pathText = newValue
        }
        submitDebounce.apply()
    }


    private fun submitEdit() {
        if (props.reportState.outputSpec().export.pathPattern == state.pathText) {
            return
        }

        props.dispatcher.dispatchAsync(ExportPathRequest(state.pathText))
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
//                    width = 20.em
                    width = 100.pct
                    marginTop = 0.5.em
//                    fontSize = 1.5.em
                }

                size = "medium"

                InputProps = object : RProps {
                    @Suppress("unused")
                    var startAdornment = child(MaterialInputAdornment::class) {
                        attrs {
                            position = "start"
                        }
//                        child(SearchIcon::class) {}
                        iconify(vaadinIconFile)
                    }
                }

                onChange = {
                    val target = it.target as HTMLInputElement
                    onValueChange(target.value)
                }

                value = state.pathText
                disabled = props.editDisabled
                onKeyDown = ::handleEnter
            }
        }
    }
}