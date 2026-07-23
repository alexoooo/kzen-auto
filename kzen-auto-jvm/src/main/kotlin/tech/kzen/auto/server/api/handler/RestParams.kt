package tech.kzen.auto.server.api.handler

import io.ktor.http.*


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
