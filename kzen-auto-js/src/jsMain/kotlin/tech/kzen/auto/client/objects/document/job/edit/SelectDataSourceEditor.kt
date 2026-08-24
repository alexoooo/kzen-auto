package tech.kzen.auto.client.objects.document.job.edit

import js.objects.unsafeJso
import react.ChildrenBuilder
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditor
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorProps
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorBase
import tech.kzen.auto.client.objects.document.common.edit.select.SelectReferenceEditorState
import tech.kzen.auto.client.service.global.ClientStateGlobal
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.NotationEvent
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.store.MirroredGraphStore


class SelectDataSourceEditor(
    props: AttributeEditorProps
) : SelectReferenceEditorBase<AttributeEditorProps, SelectReferenceEditorState>(props) {
    @Reflect
    class Wrapper(
        objectLocation: ObjectLocation,
        @Service private val clientStateGlobal: ClientStateGlobal,
        @Service private val mirroredGraphStore: MirroredGraphStore
    ) : AttributeEditor(objectLocation) {
        override fun ChildrenBuilder.child(block: AttributeEditorProps.() -> Unit) {
            SelectDataSourceEditor::class.react {
                clientStateGlobal = this@Wrapper.clientStateGlobal
                mirroredGraphStore = this@Wrapper.mirroredGraphStore
                block()
            }
        }
    }


    override fun SelectReferenceEditorState.init(props: AttributeEditorProps) {
        val graphNotation = props.clientStateGlobal.current()!!.graphStructure().graphNotation
        selected = currentSelection(graphNotation) ?: preselectedSource(graphNotation)
        options = sourceOptions(graphNotation)
    }


    private fun preselectedSource(graphNotation: GraphNotation): String? {
        if (!rawSelection(graphNotation).isNullOrEmpty()) {
            return null
        }
        val documentSources = DataSourceConventions.allDataSources(graphNotation)
            .filter { it.documentPath == props.objectLocation.documentPath }
        return documentSources.singleOrNull()?.asString()
    }


    private fun currentSelection(graphNotation: GraphNotation): String? {
        val reference = rawSelection(graphNotation)
            ?.takeIf { it.isNotEmpty() }
            ?.let(ObjectReference::tryParse)
            ?: return null
        return graphNotation.coalesce
            .locateOptional(reference, ObjectReferenceHost.ofLocation(props.objectLocation))
            ?.asString()
    }


    private fun rawSelection(graphNotation: GraphNotation): String? {
        return (graphNotation.firstAttribute(
            props.objectLocation, props.attributeName) as? ScalarAttributeNotation)?.value
    }


    private fun sourceOptions(graphNotation: GraphNotation): Array<SelectOption> {
        return DataSourceConventions.allDataSources(graphNotation)
            .sortedWith(compareBy<ObjectLocation>(
                { it.documentPath != props.objectLocation.documentPath },
                { it.documentPath.asString() },
                { it.objectPath.asString() }))
            .map { source ->
                val sourceType = graphNotation
                    .inheritanceChain(source)
                    .drop(1)
                    .firstOrNull()
                    ?.objectPath
                    ?.name
                    ?.value
                val option: SelectOption = unsafeJso {
                    value = source.asString()
                    label = source.objectPath.name.value
                    detail = sourceType
                    group = source.documentPath.asString()
                }
                option
            }
            .toTypedArray()
    }


    override suspend fun onNotationEvent(
        event: NotationEvent,
        graphDefinition: GraphDefinitionAttempt
    ) {
        refresh(graphDefinition.graphStructure.graphNotation)
    }


    override suspend fun onStoreRefresh(graphDefinitionAttempt: GraphDefinitionAttempt) {
        refresh(graphDefinitionAttempt.graphStructure.graphNotation)
    }


    private fun refresh(graphNotation: GraphNotation) {
        setSelected(currentSelection(graphNotation) ?: preselectedSource(graphNotation))
        setOptions(sourceOptions(graphNotation))
    }


    override fun wireValue(optionKey: String): String {
        val source = ObjectLocation.parse(optionKey)
        val retainDocument = source.documentPath != props.objectLocation.documentPath
        return source.toReference().crop(retainDocument).asString()
    }
}
