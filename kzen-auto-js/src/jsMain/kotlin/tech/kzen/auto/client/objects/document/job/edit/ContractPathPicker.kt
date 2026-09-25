package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.IconButton
import react.ChildrenBuilder
import react.Key
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.path.ContractPathTree
import tech.kzen.auto.common.objects.document.job.path.ProjectionPath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.renderName
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.FontFamily
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface ContractPathPickerProps: Props {
    // The upstream Worker's output contract (null: [note] says why there is none)
    var upstream: DataContract?
    var note: String?

    // Path texts already chosen, offered disabled
    var chosen: Set<String>

    // Whether a list or map opens into its `[*]` element; a writer column never unnests
    var unnest: Boolean

    var onAdd: (ProjectionPath) -> Unit
}


external interface ContractPathPickerState: State {
    // Tree nodes the user opened (by path text); a recursive reference is offered collapsed until opened.
    var expanded: Set<String>
}


//---------------------------------------------------------------------------------------------------------------------
// The design-time path picker shared by the Paths and writer-columns editors: walks the upstream Worker's output
// contract (ContractPathTree, the same common code the runtime binds with) one level at a time. Scalar leaves are
// added with a click, records (and `meta`, the value's metadata) open into their fields, a list or map opens into
// its `[*]` element when [ContractPathPickerProps.unnest] allows, and a recursive reference stays collapsed until
// opened, so the picker never pre-expands a cycle nor touches the source.
class ContractPathPicker(
    props: ContractPathPickerProps
):
    RComponent<ContractPathPickerProps, ContractPathPickerState>(props)
{
    override fun ContractPathPickerState.init(props: ContractPathPickerProps) {
        expanded = setOf()
    }


    private fun toggleExpanded(pathText: String) {
        // Read the current state outside the builder: its receiver is the partial state being merged
        val current = state.expanded
        val next = if (pathText in current) current - pathText else current + pathText
        setState {
            expanded = next
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val upstream = props.upstream
        if (upstream == null) {
            div {
                css {
                    fontSize = 0.8.em
                    color = Color("rgba(0, 0, 0, 0.6)")
                }
                +(props.note ?: "Upstream contract not available")
            }
            return
        }
        div {
            css {
                marginTop = 0.25.em
                fontSize = 0.85.em
            }
            for (candidate in ContractPathTree.roots(upstream)) {
                renderCandidate(upstream, candidate, 0)
            }
        }
    }


    private fun ChildrenBuilder.renderCandidate(
        upstream: DataContract,
        candidate: ContractPathTree.Candidate,
        depth: Int
    ) {
        val pathText = candidate.path.asString()
        val expandable = candidate.expandable && (props.unnest ||
            (candidate.kind != ContractPathTree.Kind.List && candidate.kind != ContractPathTree.Kind.Map))
        val open = expandable && pathText in state.expanded
        div {
            key = Key(pathText)
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginLeft = (depth * 1.25).em
            }
            if (expandable) {
                IconButton {
                    size = mui.material.Size.small
                    title = if (open) "Collapse" else "Expand"
                    onClick = { toggleExpanded(pathText) }
                    icon(if (open) "material-symbols:expand-more" else "material-symbols:chevron-right") {}
                }
            }
            else if (candidate.selectable) {
                val chosen = pathText in props.chosen
                IconButton {
                    size = mui.material.Size.small
                    disabled = chosen
                    title = if (chosen) "Already chosen" else "Add $pathText"
                    onClick = { props.onAdd(candidate.path) }
                    icon("material-symbols:add") {}
                }
            }
            span {
                css {
                    fontFamily = FontFamily.monospace
                }
                +candidate.label
            }
            span {
                css {
                    marginLeft = 0.5.em
                    color = Color("rgba(0, 0, 0, 0.55)")
                }
                +kindLabel(candidate.kind, expandable)
            }
        }
        if (open) {
            for (child in ContractPathTree.children(upstream, candidate)) {
                renderCandidate(upstream, child, depth + 1)
            }
        }
    }


    private fun kindLabel(kind: ContractPathTree.Kind, expandable: Boolean): String =
        when (kind) {
            is ContractPathTree.Kind.Leaf -> kind.scalar.renderName()
            ContractPathTree.Kind.Record -> "record"
            ContractPathTree.Kind.List -> if (expandable) "list [*]" else "list"
            ContractPathTree.Kind.Map -> if (expandable) "map [*]" else "map"
            is ContractPathTree.Kind.Reference -> "↻ ${kind.id}"
            is ContractPathTree.Kind.Unsupported -> kind.description
        }
}
