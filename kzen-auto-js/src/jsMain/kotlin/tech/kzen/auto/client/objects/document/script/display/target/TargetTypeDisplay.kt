package tech.kzen.auto.client.objects.document.script.display.target

import emotion.react.css
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.service.rest.ClientRestApi
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import web.cssom.*
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
/**
 * One target type's client fragments — its Target Type dropdown label, its value row in the step
 * editor, and its one-line summary in the collapsed step view. Registered as an
 * `is: TargetTypeDisplay` notation object (script-js.yaml) and autowired into the
 * TargetSpecEditor / TargetAttributeView hosts, matched by [typeName] — so a new target type
 * (including from a third-party module) contributes its UI with no edit to any shared file.
 * Counterparts: TargetSpecType (notation define/create) and TargetTypeLocator (server locate).
 */
abstract class TargetTypeDisplay(
    private val objectLocation: ObjectLocation
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }


    /** Matches the `type:` notation value (TargetSpecType.typeName). */
    abstract val typeName: String

    /** Label in the step editor's Target Type dropdown. */
    abstract val editorLabel: String

    /** False = the type is complete without a value: no value row, and switching to it in the
     *  editor commits immediately. */
    open val hasValue: Boolean get() = true


    //-----------------------------------------------------------------------------------------------------------------
    /** The value row of the step editor (nothing for value-less types). */
    open fun ChildrenBuilder.renderValueEditor(context: TargetValueEditorContext) {}


    /** One-line (or thumbnail) summary in the collapsed step view. */
    abstract fun ChildrenBuilder.renderSummary(context: TargetSummaryContext)


    /**
     * Anything beyond the raw value the summary depends on (e.g. the referenced document's
     * first crop) — the host re-renders only when this changes, keeping the graph-store
     * broadcast from re-rendering every step's summary (see the render-scoping conventions).
     */
    open fun summaryDependencies(
        value: String?,
        clientState: ClientState,
        objectLocation: ObjectLocation
    ): String? {
        return null
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The standard one-line summary chrome. */
    protected fun ChildrenBuilder.summaryText(text: String) {
        div {
            css {
                color = Color("rgba(0, 0, 0, 0.55)")
                fontSize = 0.85.em
                whiteSpace = WhiteSpace.nowrap
                overflow = Overflow.hidden
                textOverflow = TextOverflow.ellipsis
                minWidth = 0.px
            }

            +text
        }
    }


    /** The standard free-text value row (debounced typing, committed on blur). */
    protected fun ChildrenBuilder.textValueEditor(context: TargetValueEditorContext) {
        div {
            css {
                // Clear the floating label's overhang above the field's top border, which
                // otherwise collides with the Target Type field above
                marginTop = 0.75.em
            }

            TextField {
                fullWidth = true
                size = Size.small
                value = context.value ?: ""

                onChange = {
                    val target = it.target as HTMLInputElement
                    context.onValueEdit(target.value)
                }

                // Commit the pending debounced edit on focus loss, so a following separate command is
                // sequenced after this write rather than racing it (see AttributePathValueEditor).
                onBlur = { context.onEditCommit() }
            }
        }
    }
}


//---------------------------------------------------------------------------------------------------------------------
/** Host-provided surface for a value row: the current raw notation value plus the write path
 *  (debounced for typing, immediate for selections). */
class TargetValueEditorContext(
    val value: String?,
    val objectLocation: ObjectLocation,
    val clientState: ClientState?,
    val navigationGlobal: NavigationGlobal,

    /** Committed change (e.g. a selection) — writes immediately. */
    val onValueChange: (String) -> Unit,

    /** In-progress typing — write debounced. */
    val onValueEdit: (String) -> Unit,

    /** Flush a pending debounced edit (blur). */
    val onEditCommit: () -> Unit
)


class TargetSummaryContext(
    val value: String?,
    val objectLocation: ObjectLocation,
    val graphStructure: GraphStructure,
    val restClient: ClientRestApi
)
