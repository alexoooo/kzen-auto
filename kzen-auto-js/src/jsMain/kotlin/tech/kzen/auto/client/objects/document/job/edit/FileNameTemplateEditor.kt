package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.Chip
import mui.material.ChipVariant
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.State
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import react.dom.onChange
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.AttributeCommitter
import tech.kzen.auto.client.objects.document.common.edit.CommonEditUtils
import tech.kzen.auto.client.objects.document.common.edit.documentEditActivity
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.job.JobValidationChannel
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.Color
import web.cssom.FlexWrap
import web.cssom.Display
import web.cssom.AlignItems
import web.cssom.em
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface FileNameTemplateEditorState: State {
    var value: String
    var label: String?

    // `${…}` placeholders the input's metadata offers (dotted into records such as `parent`), then the extras;
    // null with [note] when the upstream contract isn't known
    var fields: List<String>?
    var note: String?
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A file-name template over the value's metadata — `Write`'s `name` ([tech.kzen.auto.server.objects.job.worker
 * .content.WriteWorker]): the text itself, plus a chip per `${…}` placeholder the input offers, which inserts it
 * at the caret. The placeholders are the scalar fields of the upstream Worker's metadata contract (from the Job's
 * validation, as the path pickers read it — [JobUpstreamSchema.upstreamContract]), dotted into nested records
 * such as `parent.name`, then the extras declared by `meta.<attr>.placeholders` (`extension` for Write). Wired via
 * `editor: FileNameTemplateEditor`.
 */
@Suppress("unused")
class FileNameTemplateEditor(
    props: AttributeEditorProps
):
    ObjectScopedComponent<AttributeEditorProps, FileNameTemplateEditorState>(props),
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
            FileNameTemplateEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val placeholdersAttributePath = AttributePath.parse("placeholders")

        // Deep enough for an archive member's archive's archive; a metadata record is plain data, never cyclic
        private const val maximumDepth = 3

        private val noteColor = Color("rgba(0, 0, 0, 0.6)")


        fun placeholder(field: String): String = "\${$field}"


        /** The dotted paths of [contract]'s scalar fields, descending into records. */
        fun scalarPaths(contract: DataContract, prefix: String = "", depth: Int = 0): List<String> {
            val expanded = contract.expanded()
            val record = expanded.structural as? DataType.Record
                ?: return listOf()
            return record.fields.flatMap { field ->
                val path = prefix + field.id.name
                val child = expanded.childOrNull(DataPathSegment.Field(field.id))?.expanded()
                    ?: return@flatMap listOf()
                when (child.structural) {
                    is DataType.Scalar -> listOf(path)
                    is DataType.Record ->
                        if (depth + 1 < maximumDepth) scalarPaths(child, "$path.", depth + 1) else listOf()
                    else -> listOf()
                }
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var validationChannel: JobValidationChannel? = null

    // Set while a typed edit is waiting to commit, so a store publication can't replace the text being typed
    private var pending = false

    // Where a chip inserts: the caret as last seen in the field, or the end when the field was never focused
    private var caret: Int? = null

    private val committer = AttributeCommitter(
        graphStore = { this.props.mirroredGraphStore },
        objectLocation = { this.props.objectLocation },
        attributePath = { AttributePath.ofName(this.props.attributeName) },
        pendingNotation = { if (pending) ScalarAttributeNotation(state.value) else null },
        onCommitted = { committed -> pending = state.value != (committed as ScalarAttributeNotation).value },
        editActivity = { documentEditActivity() })


    init {
        installContextType(DocumentBridgeContext)
    }


    override fun FileNameTemplateEditorState.init(props: AttributeEditorProps) {
        value = ""
    }


    override fun componentDidMount() {
        super.componentDidMount()
        val bridge = contextValue<DocumentBridge?>()
        validationChannel = bridge?.channel(JobValidationChannel.Key)?.also { channel ->
            channel.observe(this)
        }
        props.clientStateGlobal.current()?.let { refresh(it.graphStructure(), validationChannel?.current()) }
    }


    override fun componentWillUnmount() {
        committer.flush()
        validationChannel?.unobserve(this)
        super.componentWillUnmount()
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun onClientState(clientState: ClientState) {
        refresh(clientState.graphStructure(), validationChannel?.current())
    }


    override fun onJobValidation(validation: JobValidation?) {
        props.clientStateGlobal.current()?.graphStructure()?.let { refresh(it, validation) }
    }


    private fun refresh(graphStructure: GraphStructure, validation: JobValidation?) {
        if (props.objectLocation !in graphStructure.graphNotation.coalesce) {
            return
        }

        val label = CommonEditUtils.declaredLabel(graphStructure, props.objectLocation, props.attributeName)
        val stored = (graphStructure
            .graphNotation
            .firstAttribute(props.objectLocation, props.attributeName)
                as? ScalarAttributeNotation)
            ?.value
            ?: ""
        val value = if (pending) state.value else stored

        val extras = (graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
            ?.get(placeholdersAttributePath.toNesting())
            ?.asString())
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: listOf()

        val (upstream, upstreamNote) =
            JobUpstreamSchema.upstreamContract(graphStructure, props.objectLocation, validation)
        val metadata = upstream?.metadata
        val fields = when {
            metadata != null -> scalarPaths(metadata.contract) + extras
            else -> extras.takeIf { it.isNotEmpty() }
        }
        val note = when {
            upstream == null -> upstreamNote
            metadata == null -> "The input carries no metadata"
            else -> null
        }

        if (state.value == value && state.label == label && state.fields == fields && state.note == note) {
            return
        }
        setState {
            this.value = value
            this.label = label
            this.fields = fields
            this.note = note
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onTyped(text: String, caretAt: Int?) {
        caret = caretAt
        pending = true
        setState { value = text }
        committer.schedule()
    }


    private fun onInsert(field: String) {
        val text = state.value
        val at = (caret ?: text.length).coerceIn(0, text.length)
        val token = placeholder(field)
        val next = text.substring(0, at) + token + text.substring(at)
        caret = at + token.length
        pending = true
        setState { value = next }
        committer.cancel()
        async {
            committer.commitNow(ScalarAttributeNotation(next))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        TextField {
            fullWidth = true
            size = Size.small
            label = ReactNode(CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName), state.label))
            value = state.value

            onChange = {
                val input = it.target as HTMLInputElement
                onTyped(input.value, input.selectionStart)
            }
            onSelect = {
                caret = (it.target as HTMLInputElement).selectionStart
            }

            // Commit the pending debounced edit on focus loss (see DebouncedSubmitter's invariant)
            onBlur = { committer.flush() }
        }

        div {
            css {
                display = Display.flex
                flexWrap = FlexWrap.wrap
                alignItems = AlignItems.center
                gap = 0.25.em
                marginTop = 0.25.em
            }

            val fields = state.fields
            if (!fields.isNullOrEmpty()) {
                span {
                    css {
                        fontSize = 0.8.em
                        color = noteColor
                        marginRight = 0.25.em
                    }
                    +"Insert:"
                }
                for (field in fields) {
                    Chip {
                        key = Key(field)
                        size = Size.small
                        variant = ChipVariant.outlined
                        label = ReactNode(placeholder(field))
                        title = "Insert ${placeholder(field)} at the cursor"
                        // Keep the field focused so the caret stays where the chip inserts
                        onMouseDown = { it.preventDefault() }
                        onClick = { onInsert(field) }
                    }
                }
            }

            state.note?.let { note ->
                span {
                    css {
                        fontSize = 0.8.em
                        color = noteColor
                    }
                    +note
                }
            }
        }
    }
}
