package tech.kzen.auto.client.service.global

import tech.kzen.auto.client.service.logic.ClientLogicState
import tech.kzen.auto.client.util.NavigationRoute
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.structure.GraphStructure


data class ClientState(
    val graphDefinitionAttempt: GraphDefinitionAttempt,
    val navigationRoute: NavigationRoute,
    val clientLogicState: ClientLogicState
) {
    fun graphStructure(): GraphStructure {
        return graphDefinitionAttempt.graphStructure
    }
}