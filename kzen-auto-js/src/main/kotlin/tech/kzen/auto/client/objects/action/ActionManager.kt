//package tech.kzen.auto.client.objects.action
//
//import tech.kzen.lib.common.api.model.ObjectLocation
//
//
//// TODO: change to ClientActionManager?
//@Suppress("unused")
//class ActionManager(
//        private val actions: List<ObjectLocation>
//) {
//    //-----------------------------------------------------------------------------------------------------------------
////    companion object {
////        private const val actionParent = "Action"
////        private val className = ClassName("tech.kzen.auto.client.objects.action.ActionManager")
////
////        private val creatorReference = ObjectReference.parse("ActionManager.creator/Creator")
////        private val actionsAttribute = AttributeName("actions")
////    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
////    class Creator: ObjectCreator {
////        @Suppress("UNCHECKED_CAST")
////        override fun create(
////                objectLocation: ObjectLocation,
////                graphStructure: GraphStructure,
////                objectDefinition: ObjectDefinition,
////                partialGraphInstance: GraphInstance
////        ): Any {
//////        println("^^^^^^ ActionManager.Creator")
////            val handlesDefinition =
////                    objectDefinition.attributeDefinitions[ActionManager.actionsAttribute] as ListAttributeDefinition
////
////            val handles = mutableListOf<ObjectLocation>()
////
////            for (actionLocation in handlesDefinition.values as List<ValueAttributeDefinition>) {
////                handles.add(actionLocation.value as ObjectLocation)
////            }
////
////            return ActionManager(handles)
////        }
////    }
////
////    @Suppress("unused")
////    class Definer: ObjectDefiner {
////        override fun define(
////                objectLocation: ObjectLocation,
////                graphStructure: GraphStructure,
////                partialGraphDefinition: GraphDefinition,
////                partialGraphInstance: GraphInstance
////        ): ObjectDefinitionAttempt {
//////        println("^^^^^^ ActionManager.Definer")
////            val handleDefinitions = mutableListOf<ValueAttributeDefinition>()
////
////            for (e in graphStructure.graphNotation.coalesce.values) {
////                val isValue = e.value.attributes[NotationConventions.isName]
////                        ?.asString()
////                        ?: continue
////
//////            println("^^^^^^^^ isValue: ${e.key} - $isValue")
////                if (isValue != ActionManager.actionParent) {
////                    continue
////                }
//////            println("^^^^^^^ adding: ${e.key}")
////
////                handleDefinitions.add(ValueAttributeDefinition(e.key))
////            }
////
////            val handlesDefinition = ListAttributeDefinition(handleDefinitions)
////
////            return ObjectDefinitionAttempt(
////                    ObjectDefinition(
////                            className,
////                            mapOf(actionsAttribute to handlesDefinition),
////                            creatorReference,
////                            setOf()
////                    ),
////                    setOf(),
////                    null
////            )
////        }
////    }
//
//
////    data class Handle(
////            val location: ObjectLocation,
////            val valueCodec: ResultCodec,
////            val detailCodec: ResultCodec
////    )
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun actionLocations(): List<ObjectLocation> {
//        return actions
//    }
//}