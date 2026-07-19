package tech.kzen.auto.server.service.exec

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.location.ObjectLocation


/**
 * Why an execution target could not be instantiated, said in terms of its origin rather than the bare
 *  "Not found: <location>" the caller used to get. A location can be absent for three different reasons -
 *  creation blew up, the object (or something it depends on) failed to define, or it genuinely is not there.
 */
internal object ExecutionGraphErrors {
    fun describe(
        objectLocation: ObjectLocation,
        definitionAttempt: GraphDefinitionAttempt,
        instanceAttempt: ObjectInstanceAttempt
    ): String {
        if (instanceAttempt is ObjectInstanceAttempt.Failed) {
            return "Could not create $objectLocation: ${instanceAttempt.failure.errorMessage}"
        }

        val definitionFailure = definitionAttempt.transitiveFailures[objectLocation]
        if (definitionFailure != null) {
            val detail = definitionFailure
                .attributeErrors
                .entries
                .joinToString("; ") { "${it.key.value}: ${it.value}" }
                .ifEmpty { definitionFailure.errorMessage }

            return "$objectLocation failed to define: $detail"
        }

        // genuinely absent: no such object, or not server-allowed
        return "Not found: $objectLocation"
    }
}
