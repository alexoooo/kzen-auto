package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getObjectLocationParam
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.*


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.addObject(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val objectNotation: ObjectNotation = parameters.getParam(
        CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

    val command = AddObjectCommand(
        objectLocation,
        indexInDocument,
        objectNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeObject(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val command = RemoveObjectCommand(
        objectLocation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.shiftObject(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = ShiftObjectCommand(
        objectLocation,
        indexInDocument)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.shiftObjectTree(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = ShiftObjectTreeCommand(
        objectLocation,
        indexInDocument)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.relocateObjectTree(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val newObjectNesting: ObjectNesting = parameters.getParam(
        CommonRestApi.paramObjectNesting, ObjectNesting::parse)

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = RelocateObjectTreeRefactorCommand(
        objectLocation,
        newObjectNesting,
        indexInDocument)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.renameObject(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val objectName: ObjectName = parameters.getParam(
        CommonRestApi.paramObjectName, ::ObjectName)

    val command = RenameObjectCommand(
        objectLocation,
        objectName)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.addObjectAtAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val containingAttirute: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val objectName: ObjectName = parameters.getParam(
        CommonRestApi.paramObjectName, ::ObjectName)

    val positionInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramSecondaryPosition, PositionRelation::parse)

    val objectNotation: ObjectNotation = parameters.getParam(
        CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

    val command = AddObjectAtAttributeCommand(
        objectLocation,
        containingAttirute,
        objectName,
        positionInDocument,
        objectNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertObjectInList(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val containingList: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val indexInList: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val objectName: ObjectName = parameters.getParam(
        CommonRestApi.paramObjectName, ::ObjectName)

    val positionInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramSecondaryPosition, PositionRelation::parse)

    val objectNotation: ObjectNotation = parameters.getParam(
        CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

    val command = InsertObjectInListAttributeCommand(
        objectLocation,
        containingList,
        indexInList,
        objectName,
        positionInDocument,
        objectNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeObjectInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val command = RemoveObjectInAttributeCommand(
        objectLocation,
        attributePath)

    return applyCommand(command).asString()
}
