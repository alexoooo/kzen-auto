package tech.kzen.auto.server.api.handler.command

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.auto.server.api.handler.getObjectLocationParam
import tech.kzen.auto.server.api.handler.getParam
import tech.kzen.auto.server.api.handler.getParamList
import tech.kzen.auto.server.api.handler.getParamOrNull
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.AttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.cqrs.*


//---------------------------------------------------------------------------------------------------------------------
fun NotationCommandHandler.upsertAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributeName: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val attributeNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = UpsertAttributeCommand(
        objectLocation,
        attributeName,
        attributeNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.updateInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val attributeNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = UpdateInAttributeCommand(
        objectLocation,
        attributePath,
        attributeNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.updateAllNestingsInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributeName: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val attributeNestings: List<AttributeNesting> = parameters.getParamList(
        CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

    val attributeNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = UpdateAllNestingsInAttributeCommand(
        objectLocation,
        attributeName,
        attributeNestings,
        attributeNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.updateAllValuesInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributeName: AttributeName = parameters.getParam(
        CommonRestApi.paramAttributeName, AttributeName::parse)

    val attributeNestings: List<AttributeNesting> = parameters.getParamList(
        CommonRestApi.paramAttributeNesting, AttributeNesting::parse)

    val attributeNotations: List<AttributeNotation> = parameters.getParamList(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    require(attributeNestings.size == attributeNotations.size)

    val nestingNotations = attributeNestings.zip(attributeNotations).toMap()

    val command = UpdateAllValuesInAttributeCommand(
        objectLocation,
        attributeName,
        nestingNotations)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertListItemInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val containingList: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val indexInList: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val itemNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = InsertListItemInAttributeCommand(
        objectLocation,
        containingList,
        indexInList,
        itemNotation)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertAllListItemsInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val containingList: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val indexInList: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val itemNotations: List<AttributeNotation> = parameters.getParamList(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val command = InsertAllListItemsInAttributeCommand(
        objectLocation,
        containingList,
        indexInList,
        itemNotations)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.insertMapEntryInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

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
        objectLocation,
        containingMap,
        indexInMap,
        mapKey,
        valueNotation,
        createAncestorsIfAbsent)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val removeContainerIfEmpty: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
        ?: false

    val command = RemoveInAttributeCommand(
        objectLocation,
        attributePath,
        removeContainerIfEmpty)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeListItemInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val itemNotation: AttributeNotation = parameters.getParam(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val removeContainerIfEmpty: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
        ?: false

    val command = RemoveListItemInAttributeCommand(
        objectLocation,
        attributePath,
        itemNotation,
        removeContainerIfEmpty)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.removeAllListItemsInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val itemNotations: List<AttributeNotation> = parameters.getParamList(
        CommonRestApi.paramAttributeNotation, yamlNotationParser::parseAttribute)

    val removeContainerIfEmpty: Boolean = parameters
        .getParamOrNull(CommonRestApi.paramAttributeCleanupContainer) { i -> i == "true"}
        ?: false

    val command = RemoveAllListItemsInAttributeCommand(
        objectLocation,
        attributePath,
        itemNotations,
        removeContainerIfEmpty)

    return applyCommand(command).asString()
}


fun NotationCommandHandler.shiftInAttribute(parameters: Parameters): String {
    val objectLocation = parameters.getObjectLocationParam()

    val attributePath: AttributePath = parameters.getParam(
        CommonRestApi.paramAttributePath, AttributePath::parse)

    val newPosition: PositionRelation = parameters.getParam(
        CommonRestApi.paramPositionIndex, PositionRelation::parse)

    val command = ShiftInAttributeCommand(
        objectLocation,
        attributePath,
        newPosition)

    return applyCommand(command).asString()
}
