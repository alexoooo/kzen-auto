package tech.kzen.auto.client.objects.document.job.display.contract

import emotion.react.css
import tech.kzen.auto.client.objects.document.job.display.DataContractPresentation
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import mui.material.Tooltip
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.exec.data.type.*
import web.cssom.*

/** A contract is expanded only when its node is opened, including recursive references. */
class ContractTreeNode(props: ContractTreeNodeProps): RPureComponent<ContractTreeNodeProps, ContractTreeNodeState>(props) {
    override fun ContractTreeNodeState.init(props: ContractTreeNodeProps) {
        expanded = false
    }

    override fun ChildrenBuilder.render() {
        val contract = props.contract
        if (props.childrenOnly == true) {
            renderChildren(contract)
            return
        }
        val type = contract.structural
        val expandable = type is DataType.Record || type is DataType.Listing || type is DataType.Mapping ||
                type is DataType.Union || type is DataType.Reference
        div {
            css { padding = Padding(1.px, 0.px) }
            div {
                css { display = Display.flex; alignItems = AlignItems.center; gap = 4.px; flexWrap = FlexWrap.nowrap }
                if (expandable) {
                    button {
                        title = if (state.expanded) "Collapse ${props.label}" else "Expand ${props.label}"
                        css { border = None.none; backgroundColor = Color("transparent"); cursor = Cursor.pointer; padding = 0.px; width = 12.px; flexShrink = number(0.0) }
                        onClick = { setState { expanded = !state.expanded } }
                        +(if (state.expanded) "▾" else "▸")
                    }
                }
                else span { css { width = 12.px; flexShrink = number(0.0) } }
                span { +"${props.label}${if (props.optional) "?" else ""}:" }
                Tooltip {
                    title = ReactNode(DataContractPresentation.typeTitle(contract) + if (props.optional) " · optional field" else "")
                    span {
                        tabIndex = 0
                        css { color = Color("#687080") }
                        +DataContractPresentation.typeLabel(contract)
                    }
                }
            }
            if (state.expanded && expandable) {
                div {
                    css { marginLeft = 7.px; paddingLeft = 14.px; borderLeft = Border(1.px, LineStyle.solid, Color("#d4dce6")) }
                    val expanded = try { contract.expanded() } catch (_: RuntimeException) { null }
                    if (expanded == null) +"Definition unavailable"
                    else renderChildren(expanded)
                }
            }
        }
    }

    private fun ChildrenBuilder.renderChildren(contract: DataContract) {
        when (val type = contract.structural) {
            is DataType.Record -> type.fields.forEach { field ->
                childNode(contract.child(DataPathSegment.Field(field.id)),
                    field.id.name + if (field.id.occurrence == 0) "" else " (#${field.id.occurrence + 1})", field.optional)
            }
            is DataType.Listing -> childNode(contract.child(DataPathSegment.ListingElement), "Each item")
            is DataType.Mapping -> {
                childNode(contract.child(DataPathSegment.MappingKey), "Key")
                childNode(contract.child(DataPathSegment.MappingValue), "Value")
            }
            is DataType.Union -> type.variants.forEach { variant ->
                childNode(contract.child(DataPathSegment.Variant(variant.id)), variant.id.value)
            }
            else -> childNode(contract, "Value")
        }
    }

    private fun ChildrenBuilder.childNode(contract: DataContract, label: String, optional: Boolean = false) {
        ContractTreeNode::class.react {
            key = Key(label)
            this.contract = contract
            this.label = label
            this.optional = optional
        }
    }
}
