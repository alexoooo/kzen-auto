package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.InputLabel
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.valid.ExpressionValidationIndicator
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.logic.LogicValidationGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.spec.FormulaSpec
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.None
import web.cssom.Position
import web.cssom.em
import web.cssom.px


//---------------------------------------------------------------------------------------------------------------------
external interface FormulaMapEditorProps: AttributeEditorProps {
    // Feeds the per-editor "validating…" overlay (see ExpressionValidationIndicator) — kept on the dedicated
    // props subtype so the shared AttributeEditorProps stays single-purpose.
    var logicValidationGlobal: LogicValidationGlobal
}


external interface FormulaMapEditorState: State {
    // The Worker's committed formula map (calculated-column name -> Kotlin expression), in document order.
    // Each row owns its own in-progress text; this only tracks the committed entry SET + values.
    var formulas: Map<String, String>?
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a FormulaWorker's `formula` attribute — a FormulaSpec, i.e. a calculated-column-name -> Kotlin-expression
// map (each expression appended as a new column, referencing the record's columns by name). Wired via
// `editor: FormulaMapEditor` in the FormulaWorker archetype metadata; the generic DefaultAttributeEditor renders a
// structured map attribute as "type not supported", so the FormulaTool ribbon insert needs this dedicated editor.
// Reuses the canonical FormulaSpec command builders (the same the Report formula panel uses) to mutate the map;
// each row debounces its own text edits, so this parent only applies add / remove / update commands and re-reads
// the committed map from notation on store changes.
@Suppress("unused")
class FormulaMapEditor(
    props: FormulaMapEditorProps
):
    RComponent<FormulaMapEditorProps, FormulaMapEditorState>(props),
    LocalGraphStore.Observer
{
    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore,
        @Service private val logicValidationGlobal: LogicValidationGlobal
    ):
        AttributeEditor(objectLocation)
    {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            FormulaMapEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                logicValidationGlobal = this@Wrapper.logicValidationGlobal
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun FormulaMapEditorState.init(props: FormulaMapEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        formulas = readFormulas(graphNotation)
    }


    private fun readFormulas(graphNotation: GraphNotation): Map<String, String> {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? MapAttributeNotation
            ?: return mapOf()

        val builder = mutableMapOf<String, String>()
        for ((key, value) in attributeNotation.map) {
            builder[key.asString()] = value.asString() ?: ""
        }
        return builder
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var mounted = false


    override fun componentDidMount() {
        mounted = true
        async {
            // Unobserve runs synchronously on unmount, so registering after it would leak this observer.
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refreshFormulas(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refreshFormulas(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    // Pick up add / remove (and any external edit) of the formula entries. Value-equality gated so an unrelated
    // command elsewhere in the document doesn't re-render the rows.
    private fun refreshFormulas(graphNotation: GraphNotation) {
        if (props.objectLocation !in graphNotation.coalesce) {
            // The containing Worker was deleted; its parent card hasn't re-rendered to drop us yet.
            return
        }

        val nextFormulas = readFormulas(graphNotation)
        if (state.formulas != nextFormulas) {
            setState {
                formulas = nextFormulas
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Named apply* (not on*) so they don't shadow the child props of the same role inside the react { } blocks
    // below — there `this` is the child's Props, so `onAdd(...)` would resolve to the prop (the lambda itself)
    // and recurse infinitely.
    private fun applyAdd(columnName: String) {
        async {
            props.mirroredGraphStore.apply(
                FormulaSpec.addCommand(props.objectLocation, columnName))
        }
    }


    private fun applyUpdate(columnName: String, formula: String) {
        async {
            props.mirroredGraphStore.apply(
                FormulaSpec.updateFormulaCommand(props.objectLocation, columnName, formula))
        }
    }


    private fun applyDelete(columnName: String) {
        async {
            props.mirroredGraphStore.apply(
                FormulaSpec.removeCommand(props.objectLocation, columnName))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val formulas = state.formulas
            ?: return

        // Relative container so the "validating…" pulse (a whole-Worker signal — one formula map validates as
        // one Worker) can sit in the editor's top-right corner rather than on every row.
        div {
            css {
                position = Position.relative
            }

            InputLabel {
                sx {
                    fontSize = 0.8.em
                }
                +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
            }

            div {
                for ((columnName, formula) in formulas) {
                    div {
                        key = Key(columnName)
                        css {
                            marginBottom = 0.25.em
                        }

                        FormulaMapRow::class.react {
                            this.columnName = columnName
                            this.formula = formula
                            onUpdate = { name, value -> applyUpdate(name, value) }
                            onDelete = { name -> applyDelete(name) }
                        }
                    }
                }
            }

            AddNameForm::class.react {
                entityLabel = "calculated column"
                fieldLabel = "Calculated column name"
                isDuplicate = { name -> name in formulas.keys }
                onAdd = { name -> applyAdd(name) }
            }

            div {
                css {
                    position = Position.absolute
                    top = 0.px
                    right = 0.px
                    pointerEvents = None.none
                }

                ExpressionValidationIndicator::class.react {
                    documentPath = props.objectLocation.documentPath
                    logicValidationGlobal = props.logicValidationGlobal
                }
            }
        }
    }
}
