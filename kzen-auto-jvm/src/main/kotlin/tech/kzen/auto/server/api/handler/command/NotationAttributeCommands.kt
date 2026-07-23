package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.auto.server.api.handler.getParamList
import tech.kzen.auto.server.api.handler.getParamOrNull
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.*


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.upsertAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributeName: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val attributeNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = UpsertAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributeName,
        attributeNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.updateInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val attributeNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = UpdateInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributePath,
        attributeNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.updateAllNestingsInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributeName: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val attributeNestings: List<AttributeNesting> = parameters.getParamList(
        CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

    val attributeNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = UpdateAllNestingsInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributeName,
        attributeNestings,
        attributeNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.updateAllValuesInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributeName: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val attributeNestings: List<AttributeNesting> = parameters.getParamList(
        CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

    val attributeNotations: List<AttributeNotation> = parameters.getParamList(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    require(attributeNestings.size == attributeNotations.size)

    val nestingNotations = attributeNestings.zip(attributeNotations).toMap()

    val command = UpdateAllValuesInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributeName,
        nestingNotations)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertListItemInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val containingList: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val indexInList: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val itemNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = InsertListItemInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        containingList,
        indexInList,
        itemNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertAllListItemsInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val containingList: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val indexInList: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val itemNotations: List<AttributeNotation> = parameters.getParamList(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = InsertAllListItemsInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        containingList,
        indexInList,
        itemNotations)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertMapEntryInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val containingMap: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val indexInMap: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val mapKey: AttributeSegment = parameters.getParam(
        CommonRestApi.paramAttributeKey, AttributeSegment::parse)

    val valueNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val createAncestorsIfAbsent: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCreateContainer) { value -> value == "true" }
        ?: false

    val command = InsertMapEntryInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        containingMap,
        indexInMap,
        mapKey,
        valueNotation,
        createAncestorsIfAbsent)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val removeContainerIfEmpty: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
        ?: false

    val command = RemoveInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributePath,
        removeContainerIfEmpty)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeListItemInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val itemNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val removeContainerIfEmpty: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
        ?: false

    val command = RemoveListItemInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributePath,
        itemNotation,
        removeContainerIfEmpty)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeAllListItemsInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val itemNotations: List<AttributeNotation> = parameters.getParamList(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val removeContainerIfEmpty: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
        ?: false

    val command = RemoveAllListItemsInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributePath,
        itemNotations,
        removeContainerIfEmpty)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.shiftInAttribute(parameters: Parameters): String {
    val documentPath: DocumentPath = parameters.getParam(
        CommonRestApi.paramDocumentPath, DocumentPath::parse)

    val objectPath: ObjectPath = parameters.getParam(
        CommonRestApi.paramObjectPath, ObjectPath::parse)

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val newPosition: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = ShiftInAttributeCommand(
        ObjectLocation(documentPath, objectPath),
        attributePath,
        newPosition)

    return applyCommand(command).asString()
}
