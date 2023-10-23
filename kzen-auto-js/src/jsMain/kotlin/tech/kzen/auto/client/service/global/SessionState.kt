package tech.kzen.auto.client.service.global

import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.GraphStructure


data class SessionState(
    val graphDefinitionAttempt: GraphDefinitionAttempt,
    val navigationRoute: NavigationRoute,
    val clientLogicState: ClientLogicState,

//    val imperativeModel: ImperativeModel?,
    val activeHost: DocumentPath?,
//    val runningHosts: Set<DocumentPath>
) {
    fun graphStructure(): GraphStructure {
        return graphDefinitionAttempt.graphStructure
    }
}