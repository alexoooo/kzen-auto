package tech.kzen.auto.client.objects.document.job.edit

import emotion.react.css
import mui.material.IconButton
import mui.material.InputLabel
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
import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.common.objects.document.job.path.ContractPathTree
import tech.kzen.auto.common.objects.document.job.path.PathBinding
import tech.kzen.auto.common.objects.document.job.path.PathBindingResult
import tech.kzen.auto.common.objects.document.job.path.PathProjectionEntry
import tech.kzen.auto.common.objects.document.job.path.PathProjectionSpec
import tech.kzen.auto.common.objects.document.job.path.ProjectionPath
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.data.type.renderName
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
external interface PathProjectionEditorState: State {
    // The Worker's committed entries, in output-column order; value-compared on refresh.
    var entries: List<PathProjectionEntry>?

    // The upstream Worker's output contract per the Job's server-side validation (null: unknown yet / no
    // upstream / dynamic), and the entries bound against it — output names and errors come from here.
    var upstream: DataContract?
    var upstreamNote: String?
    var binding: PathBindingResult?

    // Tree nodes the user opened (by path text); a recursive reference is offered collapsed until opened.
    var expanded: Set<String>
}


//---------------------------------------------------------------------------------------------------------------------
// Edits a PathProjectionWorker's `paths` attribute — a PathProjectionSpec, an ordered list of path entries — by
// walking the UPSTREAM Worker's output contract (E8 item 2): scalar leaves are added with a click, a list or map
// opens into its `[*]` element (a map into `key` / `value`), and a recursive reference stays collapsed until
// opened, one level at a time, so the picker never pre-expands a cycle nor touches the source. What it offers
// and what it reports come from the same common code the runtime binds with (ContractPathTree over
// PathBinding), so the offered leaves are exactly the contract's and a saved entry the upstream no longer has
// shows as the named invalid path. The upstream contract arrives over the JobValidationChannel bridge channel
// (the editor renders under the generic AttributeEditorManager, which carries objectLocation + attributeName
// only); the upstream Worker is derived from the saved wiring (JobChannelDerivation). Wired via
// `editor: PathProjectionEditor` in the PathProjectionWorker archetype metadata.
@Suppress("unused")
class PathProjectionEditor(
    props: AttributeEditorProps
):
    RComponent<AttributeEditorProps, PathProjectionEditorState>(props),
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
            PathProjectionEditor::class.react {
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


    override fun PathProjectionEditorState.init(props: AttributeEditorProps) {
        val graphStructure = props.clientStateGlobal.current()!!.graphStructure()
        entries = readEntries(graphStructure.graphNotation)
        upstream = null
        upstreamNote = null
        binding = null
        expanded = setOf()
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


    private fun readEntries(graphNotation: GraphNotation): List<PathProjectionEntry> {
        val attributeNotation = graphNotation
            .firstAttribute(props.objectLocation, props.attributeName) as? ListAttributeNotation
            ?: return listOf()
        return try {
            PathProjectionSpec.ofNotation(attributeNotation).entries
        }
        catch (_: IllegalArgumentException) {
            listOf()
        }
    }


    // Re-read the entries and the upstream contract; value-gated so an unrelated command doesn't re-render
    private fun refresh(graphStructure: GraphStructure, validation: JobValidation?) {
        val graphNotation = graphStructure.graphNotation
        if (props.objectLocation !in graphNotation.coalesce) {
            return
        }
        val nextEntries = readEntries(graphNotation)
        val (nextUpstream, note) = upstreamContract(graphStructure, validation)
        val nextBinding = nextUpstream?.let { PathBinding.bind(PathProjectionSpec(nextEntries), it) }
        if (state.entries != nextEntries || state.upstream != nextUpstream ||
                state.upstreamNote != note || state.binding != nextBinding) {
            setState {
                entries = nextEntries
                upstream = nextUpstream
                upstreamNote = note
                binding = nextBinding
            }
        }
    }


    // The upstream Worker's OUTPUT contract from the server-side walk, or why there is none
    private fun upstreamContract(graphStructure: GraphStructure, validation: JobValidation?): Pair<DataContract?, String?> {
        val upstream = JobChannelDerivation
            .derive(graphStructure, props.objectLocation.documentPath)
            .connections
            .firstOrNull { it.downstreamWorker == props.objectLocation }
            ?.upstreamWorker
            ?: return null to "No upstream Worker: connect an input first"
        val stepValidation = validation?.workerValidations?.get(upstream.objectPath)
            ?: return null to "Upstream contract not available yet"
        val contract = stepValidation.contract
            ?: return null to "Upstream contract unknown (${upstream.objectPath.name.value})"
        if (contract.structural is DataType.Dynamic) {
            return null to "Upstream contract is dynamic: paths bind at run time"
        }
        return contract to null
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun applyAdd(path: ProjectionPath) {
        async {
            props.mirroredGraphStore.apply(PathProjectionSpec.addCommand(props.objectLocation, path))
        }
    }


    private fun applyRemove(index: Int) {
        async {
            props.mirroredGraphStore.apply(PathProjectionSpec.removeCommand(props.objectLocation, index))
        }
    }


    private fun applyAlias(index: Int, alias: String) {
        val entry = state.entries?.getOrNull(index)
            ?: return
        async {
            props.mirroredGraphStore.apply(
                PathProjectionSpec.aliasCommand(props.objectLocation, index, entry.path, alias))
        }
    }


    private fun toggleExpanded(pathText: String) {
        // Read the current state outside the builder: its receiver is the partial state being merged
        val current = state.expanded
        val next = if (pathText in current) current - pathText else current + pathText
        setState {
            expanded = next
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val entries = state.entries
            ?: return
        InputLabel {
            sx {
                fontSize = 0.8.em
            }
            +CommonEditUtils.formattedLabel(AttributePath.ofName(props.attributeName))
        }
        div {
            for ((index, entry) in entries.withIndex()) {
                renderEntry(index, entry)
            }
        }
        renderPicker(entries)
    }


    private fun ChildrenBuilder.renderEntry(index: Int, entry: PathProjectionEntry) {
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


    private fun ChildrenBuilder.renderPicker(entries: List<PathProjectionEntry>) {
        val upstream = state.upstream
        if (upstream == null) {
            div {
                css {
                    fontSize = 0.8.em
                    color = Color("rgba(0, 0, 0, 0.6)")
                }
                +(state.upstreamNote ?: "Upstream contract not available")
            }
            return
        }
        val chosen = entries.map { it.path.asString() }.toSet()
        div {
            css {
                marginTop = 0.25.em
                fontSize = 0.85.em
            }
            for (candidate in ContractPathTree.roots(upstream)) {
                renderCandidate(upstream, candidate, chosen, 0)
            }
        }
    }


    private fun ChildrenBuilder.renderCandidate(
        upstream: DataContract,
        candidate: ContractPathTree.Candidate,
        chosen: Set<String>,
        depth: Int
    ) {
        val pathText = candidate.path.asString()
        val open = pathText in state.expanded
        div {
            key = Key(pathText)
            css {
                display = Display.flex
                alignItems = AlignItems.center
                marginLeft = (depth * 1.25).em
            }
            if (candidate.expandable) {
                IconButton {
                    size = mui.material.Size.small
                    title = if (open) "Collapse" else "Expand"
                    onClick = { toggleExpanded(pathText) }
                    icon(if (open) "material-symbols:expand-more" else "material-symbols:chevron-right") {}
                }
            }
            else if (candidate.selectable) {
                IconButton {
                    size = mui.material.Size.small
                    disabled = pathText in chosen
                    title = if (pathText in chosen) "Already chosen" else "Add $pathText"
                    onClick = { applyAdd(candidate.path) }
                    icon("material-symbols:add") {}
                }
            }
            span {
                css {
                    fontFamily = FontFamily.monospace
                }
                +candidate.label
            }
            span {
                css {
                    marginLeft = 0.5.em
                    color = Color("rgba(0, 0, 0, 0.55)")
                }
                +kindLabel(candidate.kind)
            }
        }
        if (open) {
            for (child in ContractPathTree.children(upstream, candidate)) {
                renderCandidate(upstream, child, chosen, depth + 1)
            }
        }
    }


    private fun kindLabel(kind: ContractPathTree.Kind): String =
        when (kind) {
            is ContractPathTree.Kind.Leaf -> kind.scalar.renderName()
            ContractPathTree.Kind.Record -> "record"
            ContractPathTree.Kind.List -> "list [*]"
            ContractPathTree.Kind.Map -> "map [*]"
            is ContractPathTree.Kind.Reference -> "↻ ${kind.id}"
            is ContractPathTree.Kind.Unsupported -> kind.description
        }
}
