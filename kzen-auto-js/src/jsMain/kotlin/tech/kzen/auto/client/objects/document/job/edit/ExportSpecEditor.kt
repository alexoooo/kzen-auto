package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.InputAdornment
import mui.material.InputAdornmentPosition
import mui.material.InputLabel
import react.ChildrenBuilder
import react.State
import react.create
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.SelectAttributeEditor
import tech.kzen.auto.client.objects.document.common.edit.TextAttributeEditor
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.report.spec.output.OutputExportSpec
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
import web.cssom.Display
import web.cssom.em
import web.cssom.pct


//---------------------------------------------------------------------------------------------------------------------
external interface ExportSpecEditorState: State {
    // The Worker's committed export config (format / compression / path pattern), read from the merged notation.
    // Value-compared on refresh (OutputExportSpec is a data class) so an unrelated command doesn't re-render.
    var spec: OutputExportSpec?
}


//---------------------------------------------------------------------------------------------------------------------
// Edits an ExportWriterWorker's `export` attribute — an OutputExportSpec (format / compression / path-pattern),
// carried as a top-level map rather than the Report document's nested `output.export`. Wired via
// `editor: ExportSpecEditor` in the ExportWriterWorker archetype metadata; the generic DefaultAttributeEditor
// renders a structured map attribute as "type not supported", so the ExportTool ribbon insert needs this
// dedicated editor.
//
// A thin composition of the SAME reusable field editors Report's OutputExportController uses — two
// SelectAttributeEditor dropdowns (format, compression) + a debounced TextAttributeEditor (path) — pointed at the
// Job-relative `export.*` paths. Each sub-editor is self-contained: it reads its `value` from props and applies
// its own `CommonEditUtils.editCommand`. That command is an `UpdateInAttributeCommand` on a nested key, which the
// reducer makes robust for a freshly palette-inserted worker (whose `export` map is inherited-only, not in its
// own notation) by coalescing the merged archetype-default map into the instance BEFORE the nested write. This
// editor only reads the resolved spec back from notation on store changes and feeds the current values down.
@Suppress("unused")
class ExportSpecEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, ExportSpecEditorState>(props),
    LocalGraphStore.Observer
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
            ExportSpecEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ExportSpecEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        spec = readSpec(graphNotation)
    }


    // mergeAttribute (not firstAttribute) so an instance overriding just one key still resolves the archetype
    // defaults for the rest — the same read the OutputExportSpec.Definer performs.
    private fun readSpec(graphNotation: GraphNotation): OutputExportSpec? {
        val attributeNotation = graphNotation
            .mergeAttribute(props.objectLocation, props.attributeName) as? MapAttributeNotation
            ?: return null

        return OutputExportSpec.ofNotation(attributeNotation)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
        async {
            props.mirroredGraphStore.observe(this)
        }
    }


    override fun componentWillUnmount() {
        props.mirroredGraphStore.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refreshSpec(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refreshSpec(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    private fun refreshSpec(graphNotation: GraphNotation) {
        if (props.objectLocation !in graphNotation.coalesce) {
            // The containing Worker was deleted; its parent card hasn't re-rendered to drop us yet.
            return
        }

        val nextSpec = readSpec(graphNotation)
        if (state.spec != nextSpec) {
            setState {
                spec = nextSpec
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val spec = state.spec
            ?: return

        InputLabel {
            css {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }

        div {
            css {
                marginTop = 0.25.em
            }
            renderFormat(spec)
            renderCompression(spec)
        }

        renderPath(spec)
    }


    private fun ChildrenBuilder.renderFormat(spec: OutputExportSpec) {
        div {
            css {
                width = 16.em
                display = Display.inlineBlock
            }

            SelectAttributeEditor::class.react {
                labelOverride = "Format"
                options = OutputExportSpec.formatOptionLabels

                objectLocation = props.objectLocation
                attributePath = OutputExportSpec.standaloneFormatAttributePath
                mirroredGraphStore = props.mirroredGraphStore

                value = spec.format
                disabled = false
            }
        }
    }


    private fun ChildrenBuilder.renderCompression(spec: OutputExportSpec) {
        div {
            css {
                width = 16.em
                display = Display.inlineBlock
                marginLeft = 1.em
            }

            SelectAttributeEditor::class.react {
                labelOverride = "Compression"
                options = OutputExportSpec.compressionOptionLabels

                objectLocation = props.objectLocation
                attributePath = OutputExportSpec.standaloneCompressionAttributePath
                mirroredGraphStore = props.mirroredGraphStore

                value = spec.compression
                disabled = false
            }
        }
    }


    private fun ChildrenBuilder.renderPath(spec: OutputExportSpec) {
        div {
            css {
                marginTop = 1.em
                width = 100.pct
            }

            TextAttributeEditor::class.react {
                labelOverride = "Export Path Pattern"

                InputProps = unsafeJso {
                    startAdornment = InputAdornment.create {
                        position = InputAdornmentPosition.start
                        icon("material-symbols:description")
                    }
                }

                objectLocation = props.objectLocation
                attributePath = OutputExportSpec.standalonePathAttributePath
                mirroredGraphStore = props.mirroredGraphStore

                value = spec.pathPattern
                type = TextAttributeEditor.Type.PlainText
                disabled = false
            }
        }
    }
}
