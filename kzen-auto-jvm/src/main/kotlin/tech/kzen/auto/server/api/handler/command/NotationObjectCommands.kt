package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectNesting
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.*


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.addObject(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val objectNotation: ObjectNotation = parameters.getParam(
        CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

    val command = AddObjectCommand(
        ObjectLocation(documentPath, objectPath),
        indexInDocument,
        objectNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeObject(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val command = RemoveObjectCommand(
        ObjectLocation(documentPath, objectPath))

    return applyCommand(command).asString()
}


fun NotationCommandHandler.shiftObject(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = ShiftObjectCommand(
        ObjectLocation(documentPath, objectPath),
        indexInDocument)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.shiftObjectTree(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = ShiftObjectTreeCommand(
        ObjectLocation(documentPath, objectPath),
        indexInDocument)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.relocateObjectTree(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val newObjectNesting: ObjectNesting = parameters.getParam(
        CommonRestApi.paramObjectNesting, ObjectNesting::parse)

    val indexInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = RelocateObjectTreeRefactorCommand(
        ObjectLocation(documentPath, objectPath),
        newObjectNesting,
        indexInDocument)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.renameObject(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val objectName: ObjectName = parameters.getParam(
        CommonRestApi.paramObjectName, ::ObjectName)

    val command = RenameObjectCommand(
        ObjectLocation(documentPath, objectPath),
        objectName)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.addObjectAtAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val containingObjectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val containingAttirute: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val objectName: ObjectName = parameters.getParam(
        CommonRestApi.paramObjectName, ::ObjectName)

    val positionInDocument: PositionRelation = parameters.getParam(
        CommonRestApi.paramSecondaryPosition, PositionRelation::parse)

    val objectNotation: ObjectNotation = parameters.getParam(
        CommonRestApi.paramObjectNotation, yamlNotationParser::parseObject)

    val command = AddObjectAtAttributeCommand(
        ObjectLocation(documentPath, containingObjectPath),
        containingAttirute,
        objectName,
        positionInDocument,
        objectNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertObjectInList(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val containingObjectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

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
        ObjectLocation(documentPath, containingObjectPath),
        containingList,
        indexInList,
        objectName,
        positionInDocument,
        objectNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeObjectInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val containingObjectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val command = RemoveObjectInAttributeCommand(
        ObjectLocation(documentPath, containingObjectPath),
        attributePath)

    return applyCommand(command).asString()
}
