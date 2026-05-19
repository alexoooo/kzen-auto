package tech.kzen.auto.client.objects.document.custom

import emotion.react.css
import react.ChildrenBuilder
import react.Props
import react.State
import react.dom.html.ReactHTML.div
import tech.kzen.auto.client.objects.document.common.attribute.AttributeEditorManager
import tech.kzen.auto.client.service.ClientContext
import tech.kzen.auto.client.service.global.ClientState
import tech.kzen.auto.client.util.async
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.client.wrap.react
import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.InsertListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveListItemInAttributeCommand
import tech.kzen.lib.common.model.structure.notation.cqrs.RemoveObjectCommand
import tech.kzen.lib.common.service.notation.NotationConventions
import web.cssom.em


//---------------------------------------------------------------------------------------------------------------------
external interface CustomViewProps: Props {
    var documentPath: DocumentPath
    var clientState: ClientState
    var serverNotation: DocumentObjectNotation
    var attributeEditorManager: AttributeEditorManager.Wrapper
}


external interface CustomViewState: State


//---------------------------------------------------------------------------------------------------------------------
@Suppress("unused")
class CustomView(
    props: CustomViewProps
):
    RPureComponent<CustomViewProps, CustomViewState>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    private fun mainObjectLocation(): ObjectLocation =
        ObjectLocation(props.documentPath, NotationConventions.mainObjectPath)


    private fun exportsListEntries(): List<ScalarAttributeNotation> {
        val mainNotation = props.serverNotation.notations[NotationConventions.mainObjectPath]
        val exportsListAttribute = mainNotation?.get(CustomConventions.exportsListAttributeName) as? ListAttributeNotation
        return exportsListAttribute?.values.orEmpty().filterIsInstance<ScalarAttributeNotation>()
    }


    private fun exportMembership(): Map<ObjectLocation, ScalarAttributeNotation> {
        val graphNotation = props.clientState.graphStructure().graphNotation
        val mainReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation())
        return exportsListEntries().associateBy { entry ->
            graphNotation.coalesce.locate(ObjectReference.parse(entry.value), mainReferenceHost)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun onToggleExport(objectLocation: ObjectLocation) {
        async {
            val existingEntry = exportMembership()[objectLocation]
            val command = if (existingEntry != null) {
                RemoveListItemInAttributeCommand(
                    mainObjectLocation(),
                    CustomConventions.exportsListAttributePath,
                    existingEntry,
                    false)
            }
            else {
                InsertListItemInAttributeCommand(
                    mainObjectLocation(),
                    CustomConventions.exportsListAttributePath,
                    PositionRelation.at(exportsListEntries().size),
                    ScalarAttributeNotation(objectLocation.objectPath.asString()))
            }
            ClientContext.mirroredGraphStore.apply(command)
        }
    }


    private fun onDeleteObject(objectLocation: ObjectLocation) {
        async {
            val existingEntry = exportMembership()[objectLocation]
            if (existingEntry != null) {
                ClientContext.mirroredGraphStore.apply(RemoveListItemInAttributeCommand(
                    mainObjectLocation(),
                    CustomConventions.exportsListAttributePath,
                    existingEntry,
                    false))
            }
            ClientContext.mirroredGraphStore.apply(RemoveObjectCommand(objectLocation))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun ChildrenBuilder.render() {
        val graphStructure = props.clientState.graphStructure()
        val graphMetadata = graphStructure.graphMetadata
        val graphNotation = graphStructure.graphNotation
        val membership = exportMembership()

        for ((objectPath, _) in props.serverNotation.notations.map) {
            if (objectPath.name == ObjectName.main && objectPath.nesting.isRoot()) {
                continue
            }

            val objectLocation = ObjectLocation(props.documentPath, objectPath)
            val objectMetadata = graphMetadata.objectMetadata[objectLocation]
            val isAbstract = graphNotation
                .directAttribute(objectLocation, NotationConventions.abstractAttributePath)
                ?.asBoolean()
                ?: false
            val isLogic = objectMetadata?.tags?.contains(CustomConventions.logicTag) ?: false
            val isExported = objectLocation in membership

            val toggleExportHandler: (() -> Unit)? =
                if (isAbstract) null
                else { { onToggleExport(objectLocation) } }

            val deleteHandler: () -> Unit = { onDeleteObject(objectLocation) }

            div {
                css {
                    marginBottom = 1.em
                }

                CustomObject::class.react {
                    this.objectPath = objectPath
                    this.objectLocation = objectLocation
                    this.objectMetadata = objectMetadata
                    this.isAbstract = isAbstract
                    this.isLogic = isLogic
                    this.isExported = isExported
                    this.onToggleExport = toggleExportHandler
                    this.onDelete = deleteHandler
                    this.attributeEditorManager = props.attributeEditorManager
                }
            }
        }

        CustomCreate::class.react {
            this.documentPath = props.documentPath
            this.documentNotation = props.serverNotation
            this.prototypes = CustomConventions.listPrototypes(graphNotation)
        }
    }
}
