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
import web.cssom.Cursor
import web.cssom.FontStyle
import web.cssom.FontWeight
import web.cssom.Margin
import web.cssom.WhiteSpace
import web.cssom.em


/** Shared expandable contract presentation used by Worker cards and the channels between them. */
class DataContractView(
    props: DataContractViewProps
): RPureComponent<DataContractViewProps, State>(props) {
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
                if (metadata != null) {
                    facetLabel("Payload")
                }
                ContractTreeNode::class.react {
                    contract = contractDisplay.contract.payload()
                    label = ""
                    childrenOnly = true
                    optional = false
                }
                if (metadata != null) {
                    facetLabel("Metadata")
                    ContractTreeNode::class.react {
                        contract = metadata
                        label = ""
                        childrenOnly = true
                        optional = false
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


    // A value with metadata shows its two parts under their own headings
    private fun ChildrenBuilder.facetLabel(text: String) {
        div {
            css {
                marginTop = 0.25.em
                fontWeight = FontWeight.bold
            }
            +text
        }
    }
}
