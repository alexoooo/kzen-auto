package tech.kzen.auto.client.service.global

import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.auto.common.paradigm.imperative.model.ImperativeModel
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure


data class SessionState(
    val graphDefinitionAttempt: GraphDefinitionAttempt,

    val navigationRoute: NavigationRoute,

    val imperativeModel: ImperativeModel?,

    val activeHost: DocumentPath?,
    val runningHosts: Set<DocumentPath>
) {
//    fun activeHost(): DocumentPath? {
//        return imperativeModel?.frames?.get(0)?.path
//    }
    fun graphStructure(): GraphStructure {
        return graphDefinitionAttempt.graphStructure
    }
}