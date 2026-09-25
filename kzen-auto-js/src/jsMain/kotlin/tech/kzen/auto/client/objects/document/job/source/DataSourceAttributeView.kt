package tech.kzen.auto.client.objects.document.job.source

import emotion.react.css
import js.objects.unsafeJso
import mui.material.Button
import mui.material.ButtonVariant
import mui.material.Size
import react.ChildrenBuilder
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import tech.kzen.auto.client.objects.document.StageObjectLocator
import tech.kzen.auto.client.objects.document.bridge.DocumentBridge
import tech.kzen.auto.client.objects.document.bridge.DocumentBridgeContext
import tech.kzen.auto.client.objects.document.common.attribute.AttributeView
import tech.kzen.auto.client.objects.document.common.attribute.AttributeViewProps
import tech.kzen.auto.client.objects.document.common.scope.ObjectScopedComponent
import tech.kzen.auto.client.objects.document.job.JobValidationChannel
import tech.kzen.auto.client.objects.document.job.display.DataContractDisplay
import tech.kzen.auto.client.objects.document.job.display.DataContractView
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.service.global.NavigationGlobal
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.contextValue
import tech.kzen.auto.client.wrap.iconify.icon
import tech.kzen.auto.client.wrap.installContextType
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.setState
import tech.kzen.auto.common.data.schema.AuthoredRecordSchemaDraft
import tech.kzen.auto.common.data.schema.RecordSchemaConventions
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.auto.common.objects.document.data.schema.DataSchemaConventions
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.AddObjectCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.UpsertAttributeCommand
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.MirroredGraphError
import tech.kzen.lib.common.service.store.MirroredGraphStore
import tech.kzen.lib.common.util.naming.NextAvailableName
import tech.kzen.lib.platform.collect.toPersistentMap
import web.cssom.*


class DataSourceAttributeView(
    props: DataSourceAttributeViewProps
) :
    ObjectScopedComponent<DataSourceAttributeViewProps, DataSourceAttributeViewState>(props),
    JobValidationChannel.Observer
{
    companion object {
        private val formatAttributeName = AttributeName("format")
        private val schemaAttributeName = AttributeName("schema")
    }


    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val navigationGlobal: NavigationGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ) : AttributeView(objectLocation) {
        override fun ChildrenBuilder.child(block: AttributeViewProps.() -> Unit) {
            DataSourceAttributeView::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                navigationGlobal = this@Wrapper.navigationGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    init {
        installContextType(DocumentBridgeContext)
    }


    private val objectLocator = StageObjectLocator(props.navigationGlobal)
    private var validationChannel: JobValidationChannel? = null
    private var observedSource: ObjectLocation? = null


    override fun DataSourceAttributeViewState.init(props: DataSourceAttributeViewProps) {
        openDocumentPath = null
        sourceLocation = null
        sourceType = null
        missingReference = null
        validation = null
        authoring = false
        authoringError = null
    }


    override fun componentDidMount() {
        validationChannel = contextValue<DocumentBridge?>()?.channel(JobValidationChannel.Key)?.also {
            it.observe(this)
        }
        super.componentDidMount()
        onJobValidation(validationChannel?.current())
    }


    override fun componentWillUnmount() {
        validationChannel?.unobserve(this)
        observedSource = null
        super.componentWillUnmount()
    }


    override fun onClientState(clientState: ClientState) {
        val graphNotation = clientState.graphStructure().graphNotation
        val rawReference = (graphNotation.firstAttribute(
            props.objectLocation,
            AttributePath.ofName(props.attributeName)
        ) as? ScalarAttributeNotation)?.value.orEmpty()
        val sourceLocation = rawReference
            .takeIf { it.isNotEmpty() }
            ?.let(ObjectReference::tryParse)
            ?.let { graphNotation.coalesce.locateOptional(
                it, ObjectReferenceHost.ofLocation(props.objectLocation)) }
        val sourceType = sourceLocation?.let { source ->
            graphNotation.inheritanceChain(source).drop(1).firstOrNull()?.objectPath?.name?.value
        }
        val missingReference = rawReference.takeIf { it.isNotEmpty() && sourceLocation == null }

        observedSource = sourceLocation
        if (state.openDocumentPath == clientState.navigationRoute.documentPath &&
                state.sourceLocation == sourceLocation &&
                state.sourceType == sourceType &&
                state.missingReference == missingReference
        ) {
            return
        }

        setState {
            openDocumentPath = clientState.navigationRoute.documentPath
            this.sourceLocation = sourceLocation
            this.sourceType = sourceType
            this.missingReference = missingReference
        }
    }


    override fun onJobValidation(validation: JobValidation?) {
        val next = validation?.workerValidations?.get(props.objectLocation.objectPath)
        if (state.validation != next) {
            setState { this.validation = next }
        }
    }


    /** A record type read from data before Run, which can be saved as an editable schema. */
    private fun inferredContract(): DataContract? {
        val validation = state.validation
            ?: return null
        if (validation.provenance == null || validation.errorMessage != null) {
            return null
        }
        return validation.contract?.payload()?.takeIf { it.structural is DataType.Record }
    }


    private fun editableFormatLocation(): ObjectLocation? {
        val source = observedSource
            ?: return null
        val graphNotation = props.clientStateGlobal.current()?.graphStructure()?.graphNotation
            ?: return null
        val value = (graphNotation.firstAttribute(source, formatAttributeName) as? ScalarAttributeNotation)
            ?.value
            ?: return null
        val format = ObjectReference.tryParse(value)?.let {
            graphNotation.coalesce.locateOptional(it, ObjectReferenceHost.ofLocation(source))
        } ?: return null
        val document = graphNotation.documents[format.documentPath]
            ?: return null
        return format.takeIf { CustomConventions.isCustomDocument(document) }
    }


    private fun onCreateSchema() {
        if (state.authoring) {
            return
        }
        val contract = inferredContract()
            ?: return
        val draft = AuthoredRecordSchemaDraft.from(contract)
            ?: return
        val format = editableFormatLocation()
        if (format == null) {
            setState { authoringError = "Create a shared format before creating an editable schema." }
            return
        }
        val graphStructure = props.clientStateGlobal.current()?.graphStructure()
            ?: return
        val graphNotation = graphStructure.graphNotation
        val marker = graphNotation.coalesce.locateOptional(
            ObjectReference.ofRootName(RecordSchemaConventions.objectName))
            ?: return
        val creation = CustomConventions.listPrototypes(graphStructure).firstOrNull { candidate ->
            marker in graphNotation.inheritanceChain(candidate.prototype)
        } ?: return
        val document = graphNotation.documents[format.documentPath]
            ?: return
        val root = NotationConventions.mainObjectPath
        val taken = document.objects.notations.map.keys.map { it.name.value }.toSet()
        val name = NextAvailableName.find(
            creation.prototype.objectPath.name.value,
            range = 2 .. 1000) { it !in taken }
            ?: return
        val schemaPath = root.nest(CustomConventions.objectsAttributePath, ObjectName(name))
        val schemaLocation = format.documentPath.toObjectLocation(schemaPath)
        val fields = MapAttributeNotation(draft.fields.map { (fieldName, fieldSpec) ->
            AttributeSegment.ofKey(fieldName) to fieldSpec.asNotation()
        }.toPersistentMap())
        val body = creation.body.upsertAttribute(DataSchemaConventions.fieldsAttributeName, fields)

        setState {
            authoring = true
            authoringError = null
        }
        async {
            val added = props.mirroredGraphStore.apply(AddObjectCommand(
                schemaLocation,
                PositionRelation.at(document.objects.notations.map.size),
                body))
            if (added is MirroredGraphError) {
                setState {
                    authoring = false
                    authoringError = added.error.message ?: added.error.toString()
                }
                return@async
            }
            val selected = props.mirroredGraphStore.apply(UpsertAttributeCommand(
                format,
                schemaAttributeName,
                ScalarAttributeNotation(schemaLocation.toReference().crop(retainPath = false).asString())))
            if (selected is MirroredGraphError) {
                setState {
                    authoring = false
                    authoringError = selected.error.message ?: selected.error.toString()
                }
                return@async
            }
            setState { authoring = false }
            objectLocator.locate(schemaLocation, state.openDocumentPath)
        }
    }


    override fun ChildrenBuilder.render() {
        state.missingReference?.let { reference ->
            span {
                css {
                    fontSize = 0.85.em
                    color = Color("#c62828")
                }
                +"Source missing: $reference"
            }
            return
        }

        val source = state.sourceLocation
        if (source == null) {
            span {
                css {
                    fontSize = 0.85.em
                    color = Color("rgba(0, 0, 0, 0.55)")
                }
                +"Source not selected"
            }
            return
        }

        div {
            css {
                display = Display.flex
                flexDirection = FlexDirection.column
                gap = 0.35.em
            }
            a {
                css {
                    display = Display.inlineFlex
                    alignItems = AlignItems.center
                    maxWidth = 100.pct
                    color = Color("rgba(0, 0, 0, 0.6)")
                    textDecoration = Globals.initial
                    cursor = Cursor.pointer

                    "&:hover" { color = Color("#1565ff") }
                }
                href = NavigationRoute(source.documentPath, RequestParams.empty).toFragment()
                onClick = { event ->
                    event.preventDefault()
                    event.stopPropagation()
                    objectLocator.locate(source, state.openDocumentPath)
                }

                val type = state.sourceType ?: "Data source"
                +"$type \"${source.objectPath.name.value}\""
                icon("material-symbols:open-in-new") {
                    style = unsafeJso {
                        fontSize = 1.em
                        marginLeft = 0.25.em
                    }
                }
            }

            renderValidatedType()
        }
    }


    // What this Worker's items are, as validation typed them: declared, or read from the source's data before Run
    private fun ChildrenBuilder.renderValidatedType() {
        DataContractView::class.react {
            display = DataContractDisplay.of(state.validation)
        }

        val draft = inferredContract()?.let(AuthoredRecordSchemaDraft::from)
        if (draft != null) {
            Button {
                variant = ButtonVariant.text
                size = Size.small
                disabled = state.authoring
                title = if (editableFormatLocation() == null) {
                    "Create a shared format before materializing the schema"
                }
                else {
                    "Create an editable schema from the type read before Run"
                }
                onClick = { onCreateSchema() }
                +(if (state.authoring) "Creating schema…" else "Create editable schema")
            }
        }
        state.authoringError?.let { error ->
            div {
                css {
                    color = Color("#c62828")
                    fontSize = 0.8.em
                }
                +error
            }
        }
    }
}
