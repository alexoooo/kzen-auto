package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import react.ChildrenBuilder
import react.Key
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.input
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.*


class FormulaCarryEditor(props: AttributeEditorProps):
    RComponent<AttributeEditorProps, FormulaCarryEditorState>(props), LocalGraphStore.Observer
{
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ): AttributeEditor(objectLocation) {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            FormulaCarryEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }

    override fun FormulaCarryEditorState.init(props: AttributeEditorProps) {
        val graph = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        notation = graph.firstAttribute(props.objectLocation, props.attributeName)
        replacing = replacing(graph)
        saving = false
        error = null
    }

    private fun replacing(graph: GraphNotation): Boolean =
        !graph.firstAttribute(props.objectLocation, AttributeName("payload")).asString().isNullOrBlank()

    private var mounted = false
    override fun componentDidMount() {
        mounted = true
        async { if (mounted) props.mirroredGraphStore.observe(this) }
    }
    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
    }
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) { refresh(graphDefinition.graphStructure.graphNotation) }
    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}
    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refresh(graphDefinitionAttempt.graphStructure.graphNotation)
    }
    private fun refresh(graph: GraphNotation) {
        if (props.objectLocation !in graph.coalesce) return
        val next = graph.firstAttribute(props.objectLocation, props.attributeName)
        val replacing = replacing(graph)
        if (next != state.notation || replacing != state.replacing) setState {
            notation = next
            this.replacing = replacing
        }
    }

    private fun save(notation: AttributeNotation) {
        if (state.saving) return
        setState { saving = true; error = null }
        async {
            try {
                val result = props.mirroredGraphStore.apply(
                    UpsertAttributeCommand(props.objectLocation, props.attributeName, notation))
                if (result is MirroredGraphError) setState { error = result.error.message }
            }
            finally { if (mounted) setState { saving = false } }
        }
    }

    private fun selections(): Map<String, String> =
        (state.notation as? MapAttributeNotation)?.map?.entries?.associate {
            it.key.asString() to requireNotNull(it.value.asString())
        }.orEmpty()

    private fun saveFields(fields: Map<String, String>) {
        save(MapAttributeNotation(fields.map { (source, rename) ->
            AttributeSegment.ofKey(source) to ScalarAttributeNotation(rename)
        }.toMap().toPersistentMap()))
    }

    override fun ChildrenBuilder.render() {
        if (!state.replacing) {
            div {
                css { fontSize = 0.85.em; color = Color("#687080") }
                +"All input fields are retained. Carry applies only when Payload replaces the object."
            }
            return
        }
        val mode = if (state.notation is MapAttributeNotation) "Selected" else
            if (state.notation?.asString() == "all") "All" else "None"
        div {
            css { display = Display.flex; gap = 5.px; alignItems = AlignItems.center }
            span { +"Carry fields" }
            for (choice in listOf("None", "All", "Selected")) button {
                disabled = state.saving || mode == choice
                onClick = { save(when (choice) {
                    "Selected" -> MapAttributeNotation.empty
                    "All" -> ScalarAttributeNotation("all")
                    else -> ScalarAttributeNotation("none")
                }) }
                +choice
            }
        }
        if (mode == "Selected") {
            val fields = selections()
            fields.forEach { (source, rename) ->
                div {
                    key = Key(source + ":" + rename)
                    css { display = Display.flex; gap = 5.px; alignItems = AlignItems.center; marginTop = 5.px }
                    span { +source }
                    input {
                        placeholder = "Rename (optional)"
                        title = "Rename $source"
                        defaultValue = rename
                        disabled = state.saving
                        onBlur = { event ->
                            val next = event.currentTarget.value
                            if (next != rename) saveFields(selections() + (source to next))
                        }
                    }
                    button {
                        disabled = state.saving
                        title = "Remove $source"
                        onClick = { saveFields(selections() - source) }
                        +"Remove"
                    }
                }
            }
            if (!state.saving) AddNameForm::class.react {
                entityLabel = "carried field"
                fieldLabel = "Source field name"
                isDuplicate = { it in fields }
                onAdd = { saveFields(selections() + (it to "")) }
            }
        }
        state.error?.let { message -> div { +message } }
    }
}
