package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.IconButton
import mui.material.InputLabel
import mui.material.Size
import mui.system.sx
import react.ChildrenBuilder
import react.Key
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.job.JobValidationChannel
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RComponent
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.objects.document.job.path.PathBinding
import tech.kzen.auto.common.objects.document.job.path.PathBindingResult
import tech.kzen.auto.common.objects.document.job.path.PathProjectionSpec
import tech.kzen.auto.common.objects.document.job.path.ProjectionPath
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec
import tech.kzen.auto.common.objects.document.job.path.WriterColumnSpec.WriterColumn
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.FontFamily
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface WriterColumnsEditorState: State {
    // The writer's committed column selection; value-compared on refresh.
    var spec: WriterColumnSpec?

    // The upstream Worker's output contract per the Job's server-side validation (null: [upstreamNote] says why),
    // and the path columns bound against it — output names and errors come from here.
    var upstream: DataContract?
    var upstreamNote: String?
    var binding: PathBindingResult?
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a writer's `columns` attribute — a WriterColumnSpec, an ordered list of columns — as rows: `*` is every
// payload column, and a path column (a metadata field such as `meta.name`, or a nested scalar) has an alias and
// its binding error. Paths are picked from the upstream Worker's output contract in the shared ContractPathPicker,
// which never unnests here (a writer writes one row per value). No columns means `*`; the first path added keeps
// the payload's columns by writing `*` before it (WriterColumnSpec.addPathCommand). Wired via
// `editor: WriterColumnsEditor` on CsvWriterWorker's and ExportWriterWorker's `columns` metadata.
@Suppress("unused")
class WriterColumnsEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, WriterColumnsEditorState>(props),
    LocalGraphStore.Observer,
    JobValidationChannel.Observer
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
            WriterColumnsEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var validationChannel: JobValidationChannel? = null
    private var mounted = false


    init {
        installContextType(DocumentBridgeContext)
    }


    override fun WriterColumnsEditorState.init(props: AttributeEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        spec = readSpec(graphStructure.graphNotation)
        upstream = null
        upstreamNote = null
        binding = null
    }


    override fun componentDidMount() {
        mounted = true
        val bridge = contextValue<DocumentBridge?>()
        validationChannel = bridge?.channel(JobValidationChannel.Key)?.also { channel ->
            channel.observe(this)
            props.clientStateGlobal.current()?.graphStructure()?.let { refresh(it, channel.current()) }
        }
        async {
            if (mounted) {
                props.mirroredGraphStore.observe(this)
            }
        }
    }


    override fun componentWillUnmount() {
        mounted = false
        props.mirroredGraphStore.unobserve(this)
        validationChannel?.unobserve(this)
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onCommandSuccess(
        event: NotationEvent, graphDefinition: GraphDefinitionAttempt, attachment: LocalGraphStore.Attachment
    ) {
        refresh(graphDefinition.graphStructure, validationChannel?.current())
    }


    override suspend fun onCommandFailure(
        command: NotationCommand, cause: Throwable, attachment: LocalGraphStore.Attachment
    ) {}


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refresh(graphDefinitionAttempt.graphStructure, validationChannel?.current())
    }


    override fun onJobValidation(validation: JobValidation?) {
        props.clientStateGlobal.current()?.graphStructure()?.let { refresh(it, validation) }
    }


    private fun readSpec(graphNotation: GraphNotation): WriterColumnSpec {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? ListAttributeNotation
            ?: return WriterColumnSpec.payloadFields
        return try {
            WriterColumnSpec.ofNotation(attributeNotation)
        }
        catch (_: IllegalArgumentException) {
            WriterColumnSpec.payloadFields
        }
    }


    // Re-read the columns and the upstream contract; value-gated so an unrelated command doesn't re-render
    private fun refresh(graphStructure: GraphStructure, validation: JobValidation?) {
        val graphNotation = graphStructure.graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            return
        }
        val nextSpec = readSpec(graphNotation)
        val (nextUpstream, note) = JobUpstreamSchema.upstreamContract(graphStructure, props.objectLocation, validation)
        val nextBinding = nextUpstream?.let { PathBinding.bind(PathProjectionSpec(nextSpec.pathEntries()), it) }
        if (state.spec != nextSpec || state.upstream != nextUpstream ||
                state.upstreamNote != note || state.binding != nextBinding) {
            setState {
                spec = nextSpec
                upstream = nextUpstream
                upstreamNote = note
                binding = nextBinding
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val attributePath: AttributePath
        get() = AttributePath.ofName(props.attributeName)


    private fun applyAdd(path: ProjectionPath) {
        val spec = state.spec
            ?: return
        async {
            props.mirroredGraphStore.apply(
                WriterColumnSpec.addPathCommand(props.objectLocation, props.attributeName, spec, path))
        }
    }


    private fun applyAddPayloadFields() {
        async {
            props.mirroredGraphStore.apply(
                WriterColumnSpec.addPayloadFieldsCommand(props.objectLocation, props.attributeName))
        }
    }


    private fun applyRemove(index: Int) {
        async {
            props.mirroredGraphStore.apply(
                PathProjectionSpec.removeCommand(props.objectLocation, index, attributePath))
        }
    }


    private fun applyAlias(index: Int, alias: String) {
        val column = state.spec?.columns?.getOrNull(index) as? WriterColumn.Path
            ?: return
        async {
            props.mirroredGraphStore.apply(
                PathProjectionSpec.aliasCommand(props.objectLocation, index, column.entry.path, alias, attributePath))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val spec = state.spec
            ?: return
        InputLabel {
            sx {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }
        div {
            if (spec.columns.isEmpty()) {
                renderPayloadFields(null)
            }
            for ((index, column) in spec.columns.withIndex()) {
                when (column) {
                    WriterColumn.PayloadFields -> renderPayloadFields(index)
                    is WriterColumn.Path -> renderPath(index, column)
                }
            }
        }
        if (spec.columns.isNotEmpty() && WriterColumn.PayloadFields !in spec.columns) {
            Button {
                variant = ButtonVariant.text
                size = Size.small
                onClick = { applyAddPayloadFields() }
                +"Add every payload column (*)"
            }
        }
        ContractPathPicker::class.react {
            upstream = state.upstream
            note = state.upstreamNote
            chosen = spec.pathEntries().map { it.path.asString() }.toSet()
            unnest = false
            onAdd = ::applyAdd
        }
    }


    // `*` at [index], or the implicit default (null) when there are no columns
    private fun ChildrenBuilder.renderPayloadFields(index: Int?) {
        div {
            key = Key("${index}:*")
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginBottom = 0.25.em
            }
            if (index != null) {
                IconButton {
                    title = "Remove every payload column"
                    onClick = { applyRemove(index) }
                    icon("material-symbols:delete") {}
                }
            }
            span {
                css {
                    fontFamily = FontFamily.monospace
                    fontSize = 0.85.em
                    marginRight = 0.5.em
                }
                +WriterColumnSpec.allPayloadFields
            }
            span {
                css {
                    fontSize = 0.8.em
                    color = Color("rgba(0, 0, 0, 0.6)")
                }
                +(if (index == null) "every payload column (the default)" else "every payload column")
            }
        }
    }


    private fun ChildrenBuilder.renderPath(index: Int, column: WriterColumn.Path) {
        val entry = column.entry
        val error = state.binding?.errors?.firstOrNull { it.path == entry.path }?.message
        PathProjectionEntryRow::class.react {
            key = Key("${index}:${entry.path.asString()}")
            this.index = index
            path = entry.path.asString()
            alias = entry.alias ?: ""
            outputName = entry.outputName
            this.error = error
            onAlias = ::applyAlias
            onRemove = ::applyRemove
        }
    }
}
