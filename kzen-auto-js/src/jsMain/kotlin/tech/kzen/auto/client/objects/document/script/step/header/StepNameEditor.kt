package tech.kzen.auto.client.objects.document.script.step.header

import emotion.react.css
import mui.material.IconButton
import mui.material.Size
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.edit.ObjectNameEditor
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.*


//---------------------------------------------------------------------------------------------------------------------
external interface StepNameEditorProps: react.Props {
    var objectLocation: ObjectLocation
    var description: String
    var title: String
    var mirroredGraphStore: MirroredGraphStore
}


external interface StepNameEditorState: react.State {
    var editing: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
// NB: kept as its own pure component so the (~10 non-pure) MUI IconButton subtree — ButtonBase, ripple, SvgIcon,
//     ownerState forwarding — bails out when the editor re-renders for unrelated reasons.
//     Props must be stable references (use a class field for onAction, not ::method).
external interface RenameButtonProps: react.Props {
    var onAction: () -> Unit
}


class RenameButton(props: RenameButtonProps):
    RPureComponent<RenameButtonProps, react.State>(props)
{
    override fun ChildrenBuilder.render() {
        IconButton {
            title = "Rename"
            size = Size.small

            onClick = { props.onAction() }

            icon("material-symbols:edit") {}
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
class StepNameEditor(
    props: StepNameEditorProps
):
    RPureComponent<StepNameEditorProps, StepNameEditorState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun title(graphStructure: GraphStructure, objectLocation: ObjectLocation): String {
            val titleAttributeText = graphStructure
                .graphNotation
                .firstAttribute(objectLocation, AutoConventions.titleAttributePath)
                ?.asString()

            return titleAttributeText
                ?: graphStructure.graphNotation.getString(
                    objectLocation, NotationConventions.isAttributePath)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun StepNameEditorState.init(props: StepNameEditorProps) {
        editing = false
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: stable reference — RenameButton (RPureComponent) bails out only if its onAction prop is referentially
    //     equal across renders. ::onStartEdit would create a fresh bound reference per access.
    private val onStartEditCallback: () -> Unit = { onStartEdit() }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onStartEdit() {
        if (!state.editing) {
            setState {
                editing = true
            }
        }
    }


    private fun onCloseEdit() {
        setState {
            editing = false
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                alignItems = AlignItems.center
                minWidth = 0.px
            }

            // NB: no stopPropagation on this (full-width) name-area div — the empty space around a short name
            //     must fall through to the header's expand/collapse toggle (StepHeader). Only the genuinely
            //     handled bits stop: the edit-affordance group (name text + pencil) and the whole editor while
            //     editing. The hover highlight + pencil-reveal live on that group (renderReader) — NOT here — via
            //     pure CSS (not a hover state field, which would re-reconcile sibling slots on every mouse move and
            //     flash them in React DevTools' "Highlight updates" overlay). Scoping them to the group means the
            //     "click to edit" cue only appears over the region that actually edits, never the fall-through space.

            if (state.editing) {
                renderEditor()
            }
            else {
                renderReader()
            }
        }
    }


    private fun ChildrenBuilder.renderReader() {
        // The "edit affordance" group binds the name text + pencil into one clickable unit. The whole card already
        // shows a pointer cursor and toggles expand/collapse on click, so a bare pointer over the name signals
        // nothing distinct — the user can't tell that clicking HERE edits rather than toggles. Hovering this group
        // (over the name OR the pencil) tints its background and fades the pencil in: a single, reliable cue that a
        // click opens the name editor. Clicking anywhere in the group starts editing and stops propagation so the
        // header doesn't also toggle; the empty space OUTSIDE the group still falls through to expand/collapse.
        div {
            css {
                display = Display.inlineFlex
                alignItems = AlignItems.center
                minWidth = 0.px
                maxWidth = 100.pct
                borderRadius = 4.px
                paddingTop = 0.1.em
                paddingBottom = 0.1.em
                paddingLeft = 0.25.em
                paddingRight = 0.25.em
                // offset the left padding so the name's resting position stays aligned with the summary row below;
                // the tint then bleeds 0.25em left into the icon gap, reading as a button hit-area on hover.
                marginLeft = (-0.25).em
                cursor = Cursor.pointer
                transition = "background-color 120ms ease-out".unsafeCast<Transition>()

                "&:hover" {
                    backgroundColor = Color("rgba(0, 0, 0, 0.06)")
                }
                "&:hover [data-rename-button]" {
                    opacity = number(1.0)
                }
            }

            onClick = { it.stopPropagation(); onStartEdit() }

            span {
                css {
                    fontSize = 1.5.em
                    fontWeight = FontWeight.bold
                    whiteSpace = WhiteSpace.nowrap
                    overflow = Overflow.hidden
                    textOverflow = TextOverflow.ellipsis
                    minWidth = 0.px
                }

                title = props.description

                val objectName = props.objectLocation.objectPath.name

                if (AutoConventions.isAnonymous(objectName)) {
                    +props.title
                }
                else {
                    +objectName.value
                }
            }

            // NB: hidden by default; the group's &:hover rule fades it in (data-rename-button hook). The button
            //     still drives onStartEdit itself — its own ripple makes it a real button; the group is the
            //     enlarged hit-area that lets the name text trigger the same edit.
            div {
                asDynamic()["data-rename-button"] = ""

                css {
                    display = Display.inlineFlex
                    alignItems = AlignItems.center
                    marginLeft = 0.25.em
                    opacity = number(0.0)
                    transition = "opacity 120ms ease-out".unsafeCast<Transition>()
                }

                RenameButton::class.react {
                    onAction = onStartEditCallback
                }
            }
        }
    }


    private fun ChildrenBuilder.renderEditor() {
        div {
            css {
                flexGrow = number(1.0)
                minWidth = 0.px
            }

            // stopPropagation so interacting with the text field (clicks to place the caret, select, etc.)
            // doesn't bubble to the header toggle and collapse the step mid-edit.
            onClick = { it.stopPropagation() }

            ObjectNameEditor::class.react {
                objectLocation = props.objectLocation
                onClose = ::onCloseEdit
                mirroredGraphStore = props.mirroredGraphStore
            }
        }
    }
}
