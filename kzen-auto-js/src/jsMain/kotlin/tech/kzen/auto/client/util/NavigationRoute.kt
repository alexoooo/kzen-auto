package tech.kzen.auto.client.util

import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath


data class NavigationRoute(
        val documentPath: DocumentPath?,
        val requestParams: RequestParams
) {
    fun toFragment(): String {
        if (documentPath == null && requestParams.values.isEmpty()) {
            return ""
        }

        val pathPrefix = documentPath?.asString() ?: ""

        val paramSuffix =
            if (requestParams.values.isEmpty()) {
                ""
            }
            else {
                "?" + requestParams.asString()
            }

        return "#$pathPrefix$paramSuffix"
    }
}