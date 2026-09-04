package tech.kzen.auto.client.objects.document.common.file.format

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.file.FileSelectionEntry
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FileFormatCatalog
import tech.kzen.auto.common.data.format.FormatResolutionDetail
import tech.kzen.auto.common.data.format.FormatMaterializationIntent
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.lib.common.exec.data.shape.DataShapeResult
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.reflect.Reflect
import web.cssom.Color
import web.cssom.Display
import web.cssom.em


external interface FormatOverrideEditorHostProps: Props {
    var editors: List<FormatOverrideEditor>
    var catalog: FileFormatCatalog?
    var source: ObjectLocation
    var rowIndex: Int
    var entry: FileSelectionEntry
    var part: DataPart
    var resolution: FormatResolutionDetail
    var onFormatChanged: (String) -> Unit
    var onEncodingChanged: (String) -> Unit
    var shapeInspecting: Boolean
    var shapeResult: DataShapeResult?
    var shapeError: String?
    var onInspectColumns: () -> Unit
    var onApply: (FormatMaterializationIntent, Map<String, String?>, (String?) -> Unit) -> Unit
}


external interface FormatOverrideEditorHostState: State {
    var applying: Boolean
    var applyError: String?
}


class FormatOverrideEditorHost(
    props: FormatOverrideEditorHostProps
): RPureComponent<FormatOverrideEditorHostProps, FormatOverrideEditorHostState>(props) {
    private var mounted = false

    sealed interface Selection {
        data class Available(
            val format: ConfiguredFormatDetail,
            val editor: FormatOverrideEditor
        ): Selection

        data class Unavailable(val explanation: String): Selection
    }

    override fun FormatOverrideEditorHostState.init(props: FormatOverrideEditorHostProps) {
        applying = false
        applyError = null
    }

    override fun componentDidMount() {
        mounted = true
    }

    override fun componentWillUnmount() {
        mounted = false
    }

    private fun apply(intent: FormatMaterializationIntent, overrides: Map<String, String?>) {
        if (state.applying) {
            return
        }
        setState {
            applying = true
            applyError = null
        }
        props.onApply(intent, overrides) { error ->
            if (mounted) {
                setState {
                    applying = false
                    applyError = error
                }
            }
        }
    }

    companion object {
        internal fun selection(
            catalog: FileFormatCatalog?,
            resolution: FormatResolutionDetail,
            editors: List<FormatOverrideEditor>
        ): Selection {
            val catalogValue = catalog
                ?: return Selection.Unavailable(
                    "Quick controls are unavailable while format information is loading.")
            val formatReference = resolution.concreteFormatReference
                ?: return Selection.Unavailable(
                    "Quick controls are unavailable because this result has no configured format.")
            val format = catalogValue.formats.find { it.reference == formatReference }
                ?: return Selection.Unavailable(
                    "Quick controls are unavailable because this format is not in the current catalog.")
            if (!format.authoringAvailable) {
                return Selection.Unavailable(
                    "${format.label} does not provide file-specific quick controls.")
            }

            val editorReference = format.overrideEditorReference
                ?: return Selection.Unavailable(
                    "${format.label} does not provide file-specific quick controls.")
            val editorName = ObjectReference.tryParse(editorReference)?.name?.objectName
            val editor = editors.find { it.name() == editorName }
                ?: return Selection.Unavailable(
                    "Quick controls for ${format.label} are not installed in this client.")
            return Selection.Available(format, editor)
        }
    }

    @Reflect
    class Wrapper(
        private val editors: List<FormatOverrideEditor>
    ): ReactWrapper<FormatOverrideEditorHostProps> {
        override fun ChildrenBuilder.child(block: FormatOverrideEditorHostProps.() -> Unit) {
            FormatOverrideEditorHost::class.react {
                editors = this@Wrapper.editors
                block()
            }
        }
    }

    override fun ChildrenBuilder.render() {
        val selection = selection(props.catalog, props.resolution, props.editors)
        when (selection) {
            is Selection.Available -> selection.editor.child(this) {
                editorState = FormatOverrideEditorState(
                    props.source,
                    props.rowIndex,
                    props.entry,
                    props.part,
                    props.resolution,
                    selection.format,
                    props.catalog?.encodings.orEmpty())
                onFormatChanged = props.onFormatChanged
                onEncodingChanged = props.onEncodingChanged
                applying = state.applying
                applyError = state.applyError
                onApply = { overrides -> apply(FormatMaterializationIntent.Override, overrides) }
            }

            is Selection.Unavailable -> div {
                css {
                    marginTop = 0.25.em
                    fontSize = 0.8.em
                    color = Color("rgba(0, 0, 0, 0.6)")
                }
                +selection.explanation
            }
        }

        renderRepeatabilityActions()
    }

    private fun ChildrenBuilder.renderRepeatabilityActions() {
        val format = props.resolution.concreteFormatReference?.let { reference ->
            props.catalog?.formats?.find { it.reference == reference }
        }
        val presentation = FormatRepeatabilityPresentation.of(
            props.resolution,
            format,
            props.shapeInspecting,
            props.shapeResult,
            props.shapeError)

        presentation.currentGuarantee?.let { guarantee ->
            div {
                css {
                    marginTop = 0.5.em
                    fontSize = 0.8.em
                    color = Color("rgba(0, 0, 0, 0.7)")
                }
                +guarantee
            }
            return
        }

        div {
            css {
                display = Display.flex
                gap = 0.5.em
                marginTop = 0.75.em
            }
            Button {
                variant = ButtonVariant.outlined
                size = Size.small
                disabled = state.applying || !presentation.makeExplicit.enabled
                title = presentation.makeExplicit.explanation
                onClick = { apply(FormatMaterializationIntent.MakeExplicit, emptyMap()) }
                +"Make explicit"
            }
            Button {
                variant = ButtonVariant.outlined
                size = Size.small
                disabled = state.applying || !presentation.lockColumns.enabled
                title = presentation.lockColumns.explanation
                onClick = { apply(FormatMaterializationIntent.LockColumns, emptyMap()) }
                +"Lock columns"
            }
            presentation.inspection?.let { inspection ->
                Button {
                    variant = ButtonVariant.text
                    size = Size.small
                    disabled = state.applying || !inspection.enabled
                    onClick = { props.onInspectColumns() }
                    +inspection.label
                }
            }
        }
        div {
            css {
                marginTop = 0.25.em
                fontSize = 0.75.em
                color = Color("rgba(0, 0, 0, 0.65)")
            }
            +"Make explicit: ${presentation.makeExplicit.explanation} "
            +"Lock columns: ${presentation.lockColumns.explanation}"
        }
    }
}
