package tech.kzen.auto.client.objects.document.script.step.display.control
//
//import tech.kzen.auto.client.objects.document.script.command.StepCommander
//import tech.kzen.auto.common.objects.document.script.ScriptDocument
//import tech.kzen.lib.common.model.location.ObjectLocation
//import tech.kzen.lib.common.model.structure.GraphStructure
//import tech.kzen.lib.common.model.structure.notation.ObjectNotation
//import tech.kzen.lib.common.model.structure.notation.PositionRelation
//import tech.kzen.lib.common.model.structure.notation.cqrs.InsertObjectInListAttributeCommand
//import tech.kzen.lib.common.model.structure.notation.cqrs.NotationCommand
//import tech.kzen.lib.common.reflect.Reflect
//
//
//@Reflect
//class MappingCommander(
//        private val stepArchetype: ObjectLocation,
//        private val itemArchetype: ObjectLocation
//):
//    StepCommander
//{
//    override fun archetypes(): Set<ObjectLocation> {
//        return setOf(stepArchetype)
//    }
//
//
//    override fun additionalCommands(
//            createStepCommand: InsertObjectInListAttributeCommand,
//            graphStructure: GraphStructure
//    ): List<NotationCommand> {
//        val containingObjectLocation = createStepCommand.insertedObjectLocation()
//
//        val newName = ScriptDocument.findNextAvailable(
//                containingObjectLocation, itemArchetype, graphStructure)
//
//        // NB: +2 offset for main Script object plus parent
//        val endOfDocumentPosition = graphStructure
//                .graphNotation
//                .documents[containingObjectLocation.documentPath]!!
//                .objects
//                .notations
//                .values
//                .size +
//                1
//
//        val objectNotation = ObjectNotation.ofParent(
//                itemArchetype.objectPath.name)
//
//        val command = InsertObjectInListAttributeCommand(
//            containingObjectLocation,
//            ScriptDocument.stepsAttributePath,
//            PositionRelation.first,
//            newName,
//            PositionRelation.at(endOfDocumentPosition),
//            objectNotation)
//
//        return listOf(command)
//    }
//}