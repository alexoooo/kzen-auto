package tech.kzen.auto.client.objects.document.job.display

import emotion.react.css
import tech.kzen.auto.client.objects.document.job.display.contract.ContractTreeNode
import react.ChildrenBuilder
import react.State
import react.dom.html.ReactHTML.details
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.summary
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.lib.common.exec.data.type.DataContract
import web.cssom.AlignItems
import web.cssom.Border
import web.cssom.Color
import web.cssom.Cursor
import web.cssom.Display
import web.cssom.FontStyle
import web.cssom.FontWeight
import web.cssom.LineStyle
import web.cssom.Margin
import web.cssom.WhiteSpace
import web.cssom.em
import web.cssom.px


/** Shared expandable contract presentation used by Worker cards and the channels between them. */
class DataContractView(
    props: DataContractViewProps
): RPureComponent<DataContractViewProps, State>(props) {
    private companion object {
        val facetDivider = Color("rgba(0, 0, 0, 0.15)")
    }


    override fun ChildrenBuilder.render() {
        val presentation = DataContractPresentation.of(props.display)
        if (presentation.details.isEmpty() && props.display !is DataContractDisplay.Contract) {
            span {
                css {
                    fontSize = 0.75.em
                    color = presentation.color
                    whiteSpace = WhiteSpace.nowrap
                }
                title = presentation.title
                +presentation.summary
            }
            return
        }

        details {
            css {
                margin = Margin(0.em, 0.em, 0.em, 0.5.em)
                color = presentation.color
                fontSize = 0.75.em
            }
            summary {
                css {
                    cursor = Cursor.pointer
                    whiteSpace = WhiteSpace.nowrap
                }
                title = presentation.title
                +presentation.summary
            }
            val contractDisplay = props.display as? DataContractDisplay.Contract
            if (contractDisplay != null) {
                contractDisplay.provenance?.let { provenance ->
                    div {
                        css {
                            fontStyle = FontStyle.italic
                        }
                        +provenance
                    }
                }
                val metadata = contractDisplay.contract.metadata
                    ?.takeIf { it.structural.fields.isNotEmpty() }
                    ?.contract
                if (metadata == null) {
                    contractTree(contractDisplay.contract.payload())
                }
                else {
                    // A value with metadata shows its two parts side by side, each under its own heading
                    div {
                        css {
                            display = Display.flex
                            alignItems = AlignItems.flexStart
                            marginTop = 0.25.em
                        }
                        facet("Payload", contractDisplay.contract.payload(), divided = false)
                        facet("Metadata", metadata, divided = true)
                    }
                }
                if (presentation.details.isNotEmpty()) {
                    details {
                        summary { css { cursor = Cursor.pointer }; +"Technical details" }
                        presentation.details.forEach { line -> div { +line } }
                    }
                }
            }
            else presentation.details.forEach { line -> div { +line } }
        }
    }


    private fun ChildrenBuilder.facet(heading: String, contract: DataContract, divided: Boolean) {
        div {
            css {
                if (divided) {
                    borderLeft = Border(1.px, LineStyle.solid, facetDivider)
                    paddingLeft = 0.75.em
                    marginLeft = 0.75.em
                }
            }
            div {
                css {
                    fontWeight = FontWeight.bold
                }
                +heading
            }
            contractTree(contract)
        }
    }


    private fun ChildrenBuilder.contractTree(contract: DataContract) {
        ContractTreeNode::class.react {
            this.contract = contract
            label = ""
            childrenOnly = true
            optional = false
        }
    }
}
