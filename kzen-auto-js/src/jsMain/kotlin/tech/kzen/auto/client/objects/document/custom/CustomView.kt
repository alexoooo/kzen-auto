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
    override fun ChildrenBuilder.render() {
        val graphStructure = props.clientState.graphStructure()
        val graphMetadata = graphStructure.graphMetadata
        val graphNotation = graphStructure.graphNotation

        val mainObjectLocation = ObjectLocation(props.documentPath, NotationConventions.mainObjectPath)
        val mainNotation = props.serverNotation.notations[NotationConventions.mainObjectPath]
        val logicListAttribute = mainNotation?.get(CustomConventions.logicAttributeName) as? ListAttributeNotation
        val logicListEntries: List<ScalarAttributeNotation> =
            logicListAttribute?.values.orEmpty().filterIsInstance<ScalarAttributeNotation>()
        val mainReferenceHost = ObjectReferenceHost.ofLocation(mainObjectLocation)
        val logicMembership: Map<ObjectLocation, ScalarAttributeNotation> =
            logicListEntries.associateBy { entry ->
                graphNotation.coalesce.locate(ObjectReference.parse(entry.value), mainReferenceHost)
            }

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
            val isLogic = graphNotation
                .mergeAttribute(objectLocation, CustomConventions.logicAttributePath)
                ?.asBoolean()
                ?: false
            val isInLogicList = objectLocation in logicMembership

            val onToggleLogicMembership: (() -> Unit)? =
                if (! isLogic || isAbstract) {
                    null
                }
                else {
                    {
                        async {
                            val existingEntry = logicMembership[objectLocation]
                            val command = if (existingEntry != null) {
                                RemoveListItemInAttributeCommand(
                                    mainObjectLocation,
                                    CustomConventions.logicAttributePath,
                                    existingEntry,
                                    false)
                            }
                            else {
                                InsertListItemInAttributeCommand(
                                    mainObjectLocation,
                                    CustomConventions.logicAttributePath,
                                    PositionRelation.at(logicListEntries.size),
                                    ScalarAttributeNotation(objectPath.name.value))
                            }
                            ClientContext.mirroredGraphStore.apply(command)
                        }
                    }
                }

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
                    this.isInLogicList = isInLogicList
                    this.onToggleLogicMembership = onToggleLogicMembership
                    this.attributeEditorManager = props.attributeEditorManager
                }
            }
        }

        CustomNew::class.react {
            this.documentPath = props.documentPath
            this.documentNotation = props.serverNotation
            this.prototypes = CustomConventions.listPrototypes(graphNotation)
        }
    }
}
