package tech.kzen.auto.client.objects.document.common.signature

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Size
import mui.material.Switch
import mui.material.TextField
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.select.ReactSelectOption
import tech.kzen.auto.client.wrap.select.reactSelectField
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.script.ScriptConventions
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.ListExecutionValue
import tech.kzen.lib.common.exec.NullExecutionValue
import tech.kzen.lib.common.exec.ScalarExecutionValue
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.persistentMapOf
import web.cssom.em
import web.cssom.minus
import web.cssom.pct
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface LogicSignatureEditorProps: Props {
    var objectLocation: ObjectLocation

    var clientStateGlobal: ClientStateGlobal
    var mirroredGraphStore: MirroredGraphStore

    // Optional per-parameter run-time values (name -> traced value); rendered next to each row while a
    // run is active. Null/absent when not running (purely presentational — supplied by the controller).
    var parameterValues: Map<String, ExecutionValue>?
}


external interface LogicSignatureEditorState: State {
    var parameters: List<LogicSignatureEditor.ParameterRow>?
    var newParameterName: String
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * Edits a Script's typed parameters. Each parameter is a ParameterBinding object in the `parameters`
 * branch (rowless — it has no body step), named by its object name and carrying a `type` TypeMetadata.
 * Renders a row per parameter (name, a class-name picker, a nullable toggle, delete) plus an add control.
 * Generic type arguments are preserved across edits but are not yet editable here (use notation directly).
 */
class LogicSignatureEditor:
    RPureComponent<LogicSignatureEditorProps, LogicSignatureEditorState>(),
    ClientStateGlobal.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    data class ParameterRow(
        val location: ObjectLocation,
        val name: String,
        val className: String,
        val nullable: Boolean,
        val generics: ListAttributeNotation?
    )


    companion object {
        private val typeAttributeName = AttributeName("type")
        private const val classKey = "class"
        private const val genericsKey = "generics"
        private const val nullableKey = "nullable"

        private const val parameterBindingArchetype = "ParameterBinding"
        private const val defaultClassName = "kotlin.Any"

        // The selectable simple types (matches FormulaStep's inferrable class set). Generic element types
        // default to Any until a nested-type picker exists; registered object types are a follow-up.
        private val classOptions: List<Pair<String, String>> = listOf(
            "kotlin.Any" to "Any",
            "kotlin.String" to "String",
            "kotlin.Int" to "Int",
            "kotlin.Long" to "Long",
            "kotlin.Double" to "Double",
            "kotlin.Boolean" to "Boolean",
            "kotlin.collections.List" to "List",
            "kotlin.collections.Set" to "Set")
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun LogicSignatureEditorState.init(props: LogicSignatureEditorProps) {
        parameters = null
        newParameterName = ""
    }


    override fun componentDidMount() {
        props.clientStateGlobal.observe(this)
    }


    override fun componentWillUnmount() {
        props.clientStateGlobal.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            // NB: deleted or renamed (this is a stale objectLocation)
            return
        }

        val documentNotation = graphNotation.documents[props.objectLocation.documentPath]
            ?: return

        val parameterPaths = documentNotation.directNestedObjectPaths(
            props.objectLocation.objectPath, ScriptConventions.parametersAttributeName)

        val newParameters = parameterPaths.map { path ->
            val location = ObjectLocation(props.objectLocation.documentPath, path)
            val typeNotation = graphNotation.firstAttribute(location, typeAttributeName) as? MapAttributeNotation

            ParameterRow(
                location = location,
                name = path.name.value,
                className = typeNotation?.get(classKey)?.asString() ?: defaultClassName,
                nullable = typeNotation?.get(nullableKey)?.asString()?.toBoolean() ?: false,
                generics = typeNotation?.get(genericsKey) as? ListAttributeNotation)
        }

        // map produces a fresh List each fire — guard with structural equality so RPureComponent's
        // shallow state comparison doesn't re-render on unchanged content.
        if (newParameters == state.parameters) {
            return
        }

        setState {
            parameters = newParameters
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onAddParameter() {
        val name = state.newParameterName.trim()
        if (name.isEmpty()) {
            return
        }

        val mainObjectPath = props.objectLocation.objectPath

        val location = ObjectLocation(
            props.objectLocation.documentPath,
            mainObjectPath.nest(
                ScriptConventions.parametersAttributePath, ObjectName(name)))

        // Parameters are the Script header, so place the new binding above main.steps in document order
        // (the serialized "signature") rather than appended at the end. Insert just before the first step,
        // or after main / the existing parameters when there are no steps yet.
        val documentNotation = props.clientStateGlobal.current()
            ?.graphStructure()
            ?.graphNotation
            ?.documents
            ?.get(props.objectLocation.documentPath)

        val insertionRelation =
            if (documentNotation == null) {
                PositionRelation.afterLast
            }
            else {
                val firstStepIndex = documentNotation
                    .directNestedObjectPaths(mainObjectPath, ScriptConventions.stepsAttributeName)
                    .minOfOrNull { documentNotation.indexOf(it).value }

                val insertIndex = firstStepIndex
                    ?: run {
                        val lastHeaderIndex = documentNotation
                            .directNestedObjectPaths(mainObjectPath, ScriptConventions.parametersAttributeName)
                            .maxOfOrNull { documentNotation.indexOf(it).value }
                            ?: documentNotation.indexOf(mainObjectPath).value
                        lastHeaderIndex + 1
                    }

                PositionRelation.at(insertIndex)
            }

        // `type` defaults to Any via the ParameterBinding archetype; set it explicitly via the picker.
        val command = AddObjectCommand(
            location,
            insertionRelation,
            ObjectNotation.ofParent(ObjectName(parameterBindingArchetype)))

        setState {
            newParameterName = ""
        }

        async {
            props.mirroredGraphStore.apply(command)
        }
    }


    private fun onRemoveParameter(location: ObjectLocation) {
        async {
            props.mirroredGraphStore.apply(RemoveObjectCommand(location))
        }
    }


    private fun onTypeChange(row: ParameterRow, className: String, nullable: Boolean) {
        val typeNotation = MapAttributeNotation(persistentMapOf(
            AttributeSegment.ofKey(classKey) to ScalarAttributeNotation(className),
            AttributeSegment.ofKey(genericsKey) to (row.generics ?: ListAttributeNotation.empty),
            AttributeSegment.ofKey(nullableKey) to ScalarAttributeNotation(nullable.toString())))

        async {
            props.mirroredGraphStore.apply(UpsertAttributeCommand(
                row.location, typeAttributeName, typeNotation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val parameters = state.parameters
            ?: return

        div {
            css {
                width = 100.pct.minus(2.em)
                paddingLeft = 1.em
            }

            div {
                css {
                    fontSize = 0.8.em
                    marginBottom = 0.25.em
                }
                +"Parameters"
            }

            for (parameter in parameters) {
                renderParameterRow(parameter)
            }

            renderAddParameter()
        }
    }


    private fun ChildrenBuilder.renderParameterRow(parameter: ParameterRow) {
        div {
            css {
                marginBottom = 0.25.em
            }

            span {
                css {
                    display = web.cssom.Display.inlineBlock
                    width = 8.em
                    marginRight = 0.5.em
                }
                +parameter.name
            }

            span {
                css {
                    display = web.cssom.Display.inlineBlock
                    width = 10.em
                    marginRight = 0.5.em
                }

                val options = classOptions
                    .map { (value, simpleLabel) ->
                        val option: ReactSelectOption = unsafeJso {
                            this.value = value
                            this.label = simpleLabel
                        }
                        option
                    }
                    .toTypedArray()

                reactSelectField(
                    selectedOption = options.find { it.value == parameter.className },
                    options = options,
                    onSelect = { onTypeChange(parameter, it.value, parameter.nullable) })
            }

            Switch {
                checked = parameter.nullable
                onChange = { e, _ -> onTypeChange(parameter, parameter.className, e.currentTarget.checked) }
            }

            span {
                css {
                    fontSize = 0.7.em
                    marginRight = 0.5.em
                }
                +"nullable"
            }

            button {
                onClick = { onRemoveParameter(parameter.location) }
                +"×"
            }

            // Run-time value (when running as a sub-logic with arguments); blank otherwise.
            val value = props.parameterValues?.get(parameter.name)
            if (value != null && value !is NullExecutionValue) {
                span {
                    css {
                        marginLeft = 0.75.em
                        fontSize = 0.85.em
                        color = web.cssom.Color("gray")
                    }
                    +"= "
                    span {
                        css {
                            fontWeight = web.cssom.FontWeight.bold
                            color = web.cssom.NamedColor.black
                        }
                        +executionValueText(value)
                    }
                }
            }
        }
    }


    private fun executionValueText(value: ExecutionValue): String {
        return when (value) {
            is ScalarExecutionValue -> value.get().toString()
            is ListExecutionValue -> value.values.map { it.get() }.toString()
            else -> value.toString()
        }
    }


    private fun ChildrenBuilder.renderAddParameter() {
        div {
            css {
                marginTop = 0.5.em
            }

            TextField {
                size = Size.small
                placeholder = "new parameter name"
                value = state.newParameterName
                onChange = {
                    val text = (it.target as HTMLInputElement).value
                    setState { newParameterName = text }
                }
            }

            button {
                onClick = { onAddParameter() }
                +"Add parameter"
            }
        }
    }
}
