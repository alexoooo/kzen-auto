package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Divider
import mui.material.IconButton
import mui.material.ListSubheader
import mui.material.Menu
import mui.material.MenuItem
import mui.material.Size
import mui.material.TextField
import react.ChildrenBuilder
import react.Key
import react.ReactNode
import react.RefObject
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
import tech.kzen.auto.client.objects.document.job.display.DataContractPresentation
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.createRef
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.inputLabelSlotProps
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.schema.HeaderLabel
import tech.kzen.auto.common.objects.document.job.JobFieldConventions
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.util.ExpressionUtils
import tech.kzen.lib.common.exec.ExecutionValue
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataPathSegment
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore
import web.cssom.AlignItems
import web.cssom.Color
import web.cssom.Display
import web.cssom.FontFamily
import web.cssom.em
import web.cssom.number
import web.dom.Element
import web.html.HTMLInputElement


//---------------------------------------------------------------------------------------------------------------------
external interface KotlinFieldEditorState: State {
    var value: String
    var label: String?
    var template: Boolean

    // From the Worker's validation: the value a blank field stands for, and the type of the expression's value
    var defaultValue: String?
    var type: String?

    // What the input offers (its columns, then its metadata dotted into records such as `parent`), then the
    // Worker's own; null with [note] when the upstream contract isn't known
    var placeholders: List<KotlinFieldEditor.Placeholder>?
    var note: String?

    var menuOpen: Boolean
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * A Kotlin field over the input's values: an expression (`Format`'s Group by), or the body of a string template when
 * `meta.<attr>.template` is true (`Write`'s Sub-folder and File name). The text itself, plus an insert button beside
 * it (as a Script expression inserts a step reference) whose menu lists what the field can name and inserts the
 * chosen one at the caret: bare in an expression, as `${…}` in a template. What it can name comes from the upstream
 * Worker's contract (from the Job's validation, as the path pickers read it — [JobUpstreamSchema.upstreamContract]):
 * its payload's columns, then its metadata's scalar fields, dotted into nested records such as `parent.name`; then
 * the Worker's own, declared with a description each by `meta.<attr>.placeholders`. The Worker's validation can add
 * the value a blank field stands for, shown as the field's placeholder, and the type of the expression's value,
 * shown below it ([JobFieldConventions]). Wired via `editor: KotlinFieldEditor`.
 */
@Suppress("unused")
class KotlinFieldEditor(
    props: AttributeEditorProps
):
    ObjectScopedComponent<AttributeEditorProps, KotlinFieldEditorState>(props),
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
            KotlinFieldEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** One insertable [reference] (Kotlin), under the menu heading [group], with [hint] (a type or a description). */
    data class Placeholder(
        val reference: String,
        val group: String,
        val hint: String
    )


    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val placeholdersAttributePath = AttributePath.parse("placeholders")
        private const val templateKey = "template"

        // The qualifier that always reaches the metadata, past a column of the same name; a metadata field of its
        // name is not a property (the qualifier wins)
        private const val metaQualifier = "meta"

        // Deep enough for an archive member's archive's archive; a metadata record is plain data, never cyclic
        private const val maximumDepth = 3

        private const val columnGroup = "Columns"
        private const val ownMetadataGroup = "This value"
        private const val workerGroup = "This Worker"

        private val noteColor = Color("rgba(0, 0, 0, 0.6)")


        /** What inserting [reference] adds: the reference itself, or `${…}` around it in a [template]. */
        fun inserted(reference: String, template: Boolean): String =
            if (template) "\${$reference}" else reference


        /**
         * What an expression over values of [upstream] can name: the payload's columns, the metadata's scalar paths
         * (through `meta.` where a column takes the bare name), then [own].
         */
        fun placeholders(upstream: DataContract, own: List<Placeholder>): List<Placeholder> {
            val columns = scalarPaths(upstream.payload(), 1)
                .map { (path, type) -> Placeholder(path.single(), columnGroup, type) }
            val columnNames = columns.map { it.reference }.toSet()
            val metadata = upstream.metadata?.let { metadata ->
                scalarPaths(metadata.contract, maximumDepth)
                    .filter { (path, _) -> path.first() != metaQualifier }
                    .map { (path, type) ->
                        val qualified = if (path.first() in columnNames) listOf(metaQualifier) + path else path
                        Placeholder(qualified.joinToString("."), metadataGroup(path), type)
                    }
            }.orEmpty()
            return columns + metadata + own
        }


        /**
         * The scalar fields of [contract], descending [depth] records deep, each as its path's names (outermost
         * first) the way an expression names them, with its type label.
         */
        private fun scalarPaths(
            contract: DataContract, depth: Int, prefix: List<String> = listOf()
        ): List<Pair<List<String>, String>> {
            val expanded = contract.expanded()
            val record = expanded.structural as? DataType.Record
                ?: return listOf()
            return record.fields.flatMap { field ->
                val path = prefix +
                    ExpressionUtils.escapeKotlinVariableName(HeaderLabel(field.id.name, field.id.occurrence))
                val child = expanded.childOrNull(DataPathSegment.Field(field.id))?.expanded()
                    ?: return@flatMap listOf()
                when (child.structural) {
                    is DataType.Scalar -> listOf(path to DataContractPresentation.typeLabel(child))
                    is DataType.Record -> if (depth > 1) scalarPaths(child, depth - 1, path) else listOf()
                    else -> listOf()
                }
            }
        }


        // Metadata fields group by where they come from: the value itself, then each `parent.` level
        private fun metadataGroup(path: List<String>): String {
            val parents = path.dropLast(1)
            return when {
                parents.isEmpty() -> ownMetadataGroup
                else -> "From " + parents.joinToString(" → ")
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private var validationChannel: JobValidationChannel? = null

    // Set while a typed edit is waiting to commit, so a store publication can't replace the text being typed
    private var pending = false

    // Where an insert lands: the caret as last seen in the field, or the end when the field was never focused
    private var caret: Int? = null

    private val menuAnchorRef: RefObject<Element> = createRef()

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


    override fun KotlinFieldEditorState.init(props: AttributeEditorProps) {
        value = ""
        template = false
        menuOpen = false
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

        val attributeMetadata = graphStructure
            .graphMetadata
            .get(props.objectLocation)
            ?.attributes
            ?.get(props.attributeName)
            ?.attributeMetadataNotation
        val template = attributeMetadata?.get(templateKey)?.asBoolean() ?: false
        val workerPlaceholders = (attributeMetadata
            ?.get(placeholdersAttributePath.toNesting())
                as? MapAttributeNotation)
            ?.map
            ?.entries
            ?.map { (key, description) ->
                Placeholder(
                    ExpressionUtils.escapeKotlinVariableName(key.asKey()), workerGroup, description.asString() ?: "")
            }
            ?: listOf()

        val (upstream, upstreamNote) =
            JobUpstreamSchema.upstreamContract(graphStructure, props.objectLocation, validation)
        val placeholders = when {
            upstream != null -> placeholders(upstream, workerPlaceholders)
            else -> workerPlaceholders
        }.takeIf { it.isNotEmpty() }
        val note = upstreamNote.takeIf { upstream == null }

        val details = validation?.workerValidations?.get(props.objectLocation.objectPath)?.details.orEmpty()
        val defaultValue = details[JobFieldConventions.defaultKey(props.attributeName)] as? String
        val type = details[JobFieldConventions.typeKey(props.attributeName)]?.let {
            DataContractPresentation.typeLabel(DataContract.ofExecutionValue(ExecutionValue.of(it)))
        }

        if (state.value == value && state.label == label && state.template == template &&
                state.defaultValue == defaultValue && state.type == type &&
                state.placeholders == placeholders && state.note == note) {
            return
        }
        setState {
            this.value = value
            this.label = label
            this.template = template
            this.defaultValue = defaultValue
            this.type = type
            this.placeholders = placeholders
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


    private fun onMenuClose() {
        setState { menuOpen = false }
    }


    private fun onInsert(reference: String) {
        val text = state.value
        val at = (caret ?: text.length).coerceIn(0, text.length)
        val token = inserted(reference, state.template)
        val next = text.substring(0, at) + token + text.substring(at)
        caret = at + token.length
        pending = true
        setState {
            value = next
            menuOpen = false
        }
        committer.cancel()
        async {
            committer.commitNow(ScalarAttributeNotation(next))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        div {
            css {
                display = Display.flex
                // Level with the field, not with the field and its type below it
                alignItems = AlignItems.flexStart
            }

            div {
                css {
                    flexGrow = number(1.0)
                }
                TextField {
                    fullWidth = true
                    size = Size.small
                    label = ReactNode(
                        CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName), state.label))
                    value = state.value

                    state.defaultValue?.let { defaultValue ->
                        placeholder = defaultValue
                        // A placeholder shows only under a raised label
                        inputLabelSlotProps = unsafeJso {
                            shrink = true
                        }
                    }
                    state.type?.let { type ->
                        helperText = ReactNode(type)
                    }

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
            }

            span {
                ref = menuAnchorRef
                title = state.note ?: "Insert a field"

                IconButton {
                    disabled = state.placeholders.isNullOrEmpty()
                    // Keep the field focused so the caret stays where the insert lands
                    onMouseDown = { it.preventDefault() }
                    onClick = { setState { menuOpen = true } }
                    icon("material-symbols:data-object") {}
                }
            }
        }

        renderMenu()
    }


    private fun ChildrenBuilder.renderMenu() {
        val placeholders = state.placeholders
            ?: return

        Menu {
            open = state.menuOpen
            onClose = ::onMenuClose
            anchorEl = menuAnchorRef.current?.let { { _ -> it } }

            var previousGroup: String? = null
            for (placeholder in placeholders) {
                if (placeholder.group != previousGroup) {
                    if (previousGroup != null) {
                        Divider { key = Key("divider:" + placeholder.group) }
                    }
                    ListSubheader {
                        key = Key("group:" + placeholder.group)
                        +placeholder.group
                    }
                    previousGroup = placeholder.group
                }

                MenuItem {
                    key = Key(placeholder.reference)
                    dense = true
                    onClick = { onInsert(placeholder.reference) }

                    span {
                        css {
                            fontFamily = FontFamily.monospace
                        }
                        +inserted(placeholder.reference, state.template)
                    }
                    span {
                        css {
                            marginLeft = 1.em
                            fontSize = 0.85.em
                            color = noteColor
                        }
                        +placeholder.hint
                    }
                }
            }
        }
    }
}
