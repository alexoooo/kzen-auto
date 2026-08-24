package tech.kzen.auto.server.service.exec

import tech.kzen.auto.common.util.AutoConventions
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt


object ServerGraphDefinition {
    fun of(attempt: GraphDefinitionAttempt): GraphDefinition {
        return attempt.transitiveSuccessful.filterDefinitions(AutoConventions.serverAllowed)
    }
}
