package tech.kzen.auto.server.api.handler

import io.ktor.http.*
import tech.kzen.auto.common.api.CommonRestApi
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath


//---------------------------------------------------------------------------------------------------------------------
internal fun <T> Parameters.getParam(
    parameterName: String,
    parser: (String) -> T
): T {
    val queryParamValues: List<String>? = getAll(parameterName)
    require(!queryParamValues.isNullOrEmpty()) { "'$parameterName' required" }
    require(queryParamValues.size == 1) { "Single '$parameterName' expected: $queryParamValues" }
    return parser(queryParamValues.single())
}


// The documentPath + objectPath parameter pair, combined into the ObjectLocation nearly every
// command / action / logic endpoint addresses.
internal fun Parameters.getObjectLocationParam(): ObjectLocation {
    return ObjectLocation(
        getParam(CommonRestApi.paramDocumentPath, DocumentPath::parse),
        getParam(CommonRestApi.paramObjectPath, ObjectPath::parse))
}


internal fun <T> Parameters.getParamList(
    parameterName: String,
    parser: (String) -> T
): List<T> {
    val queryParamValues: List<String> = getAll(parameterName)
        ?: return listOf()
    return queryParamValues.map(parser)
}


internal fun <T> Parameters.getParamOrNull(
    parameterName: String,
    parser: (String) -> T
): T? {
    val queryParamValues: List<String> = getAll(parameterName)
        ?: return null

    require(queryParamValues.isNotEmpty()) { "'$parameterName' required" }
    require(queryParamValues.size == 1) { "Single '$parameterName' expected: $queryParamValues" }

    return parser(queryParamValues.single())
}
