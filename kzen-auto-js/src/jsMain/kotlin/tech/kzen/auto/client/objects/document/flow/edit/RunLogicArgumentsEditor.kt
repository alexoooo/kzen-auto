package tech.kzen.auto.client.objects.document.flow.edit

import js.objects.unsafeJso
import mui.material.IconButton
import mui.material.Size
import mui.material.TextField
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.flow.FlowConventions
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.auto.common.objects.document.script.model.RunStepInstructions
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.PersistentList
import tech.kzen.lib.platform.collect.PersistentMap
import tech.kzen.lib.platform.collect.toPersistentList
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface RunLogicArgumentsEditorState: State {
    var parameterNames: PersistentList<String>?

    // The edited values, and the notation they were last adopted from. A client-state publish arrives on every
    // graph change, including ones this editor caused, so it re-adopts only when the notation itself moved —
    // otherwise a publish landing between a keystroke and its debounced commit would erase the typing.
    var values: PersistentMap<String, String>?
    var notationValues: PersistentMap<String, String>?

    // Non-null once a write failed, turning the edited field red; the message itself is carried by the banner.
    var errorMessage: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a logic-host vertex's `arguments`: a text literal per callee parameter the vertex's wired inputs don't
 * already bind by position. The rows are driven by the callee's declared parameters (a Script's
 * `ParameterBinding` objects, a Flow's `FlowInput` vertices), so an author sees what can be bound rather than
 * typing keys blind; a key left over from an earlier callee gets a remove affordance instead of silently
 * disappearing from view while staying in the notation.
 *
 * Values are verbatim text — the runner binds them as `String`, with no coercion to a declared parameter type.
 */
@Suppress("unused")
class RunLogicArgumentsEditor(
    props: AttributeEditorProps
):
    RPureComponent<AttributeEditorProps, RunLogicArgumentsEditorState>(props),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            RunLogicArgumentsEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // NB: `this.props` - see the shadowing note in TextAttributeEditor.
    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { AttributePath.ofName(this.props.attributeName) },
        pendingNotation = { pendingNotation() },
        onError = { message -> setState { errorMessage = message } },
        editActivity = { documentEditActivity() })


    //-----------------------------------------------------------------------------------------------------------------
    init {
        installContextType(DocumentBridgeContext)
    }


    override fun RunLogicArgumentsEditorState.init(props: AttributeEditorProps) {
        parameterNames = null
        values = null
        notationValues = null
        errorMessage = null
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
        committer.flush()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation

        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: containing vertex deleted or renamed and this objectLocation is stale
            return
        }

        val calleeLocation = RunStepInstructions.instructionsLocation(graphNotation, props.objectLocation)

        val parameterNames =
            if (calleeLocation == null) {
                listOf()
            }
            else {
                val documentNotation = graphNotation.documents[calleeLocation.documentPath]
                if (documentNotation != null && FlowConventions.isFlow(documentNotation)) {
                    FlowConventions.inputParameterNames(graphNotation, calleeLocation)
                }
                else {
                    documentNotation
                        ?.directNestedObjectPaths(
                            calleeLocation.objectPath, ScriptConventions.parametersAttributeName)
                        ?.map { it.name.value }
                        ?: listOf()
                }
            }

        val notationValues = ((graphNotation.firstAttribute(props.objectLocation, props.attributeName)
                as? MapAttributeNotation)
            ?.map
            ?.map { it.key.asKey() to (it.value as ScalarAttributeNotation).value }
            ?.toMap()
            ?: mapOf())
            .toPersistentMap()

        // NB: read prior state outside the setState lambda — it runs on an empty partial state object.
        val notationChanged = state.notationValues != notationValues

        setState {
            this.parameterNames = parameterNames.toPersistentList()
            this.notationValues = notationValues
            if (notationChanged) {
                this.values = notationValues
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // An empty field adds no notation, so an untouched row leaves the document alone.
    private fun pendingNotation(): AttributeNotation? {
        val values = state.values
            ?: return null

        return MapAttributeNotation(
            values
                .filter { it.value.isNotEmpty() }
                .map { AttributeSegment.ofKey(it.key) to ScalarAttributeNotation(it.value) as AttributeNotation }
                .toPersistentMap())
    }


    private fun onValueChange(parameterName: String, value: String) {
        val values = state.values
            ?: return

        setState {
            this.values = values.put(parameterName, value)
        }

        committer.schedule()
    }


    private fun onRemove(parameterName: String) {
        val values = state.values
            ?: return

        setState {
            this.values = values.remove(parameterName)
        }

        committer.schedule()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val parameterNames = state.parameterNames ?: return
        val values = state.values ?: return

        for (parameterName in parameterNames) {
            div {
                key = Key(parameterName)
                renderParameter(parameterName, values[parameterName] ?: "")
            }
        }

        for (unusedParameter in values.keys.minus(parameterNames)) {
            div {
                key = Key(unusedParameter)
                renderUnusedParameter(unusedParameter, values[unusedParameter] ?: "")
            }
        }
    }


    private fun ChildrenBuilder.renderParameter(parameterName: String, value: String) {
        TextField {
            fullWidth = true
            size = Size.small

            label = ReactNode(parameterName)
            this.value = value
            error = state.errorMessage != null

            onChange = {
                onValueChange(parameterName, (it.target as HTMLInputElement).value)
            }

            // Commit the pending debounced edit on focus loss, so a following separate command is
            // sequenced after this write rather than racing it (see DebouncedSubmitter's invariant).
            onBlur = { committer.flush() }
        }
    }


    private fun ChildrenBuilder.renderUnusedParameter(parameterName: String, value: String) {
        +"Unused argument: $parameterName - $value"

        IconButton {
            sx {
                marginLeft = 0.25.em
            }
            title = "Remove"

            onClick = {
                onRemove(parameterName)
            }

            icon("material-symbols:do-not-disturb-on-outline") {
                style = unsafeJso {
                    fontSize = 1.5.em
                }
            }
        }
    }
}
