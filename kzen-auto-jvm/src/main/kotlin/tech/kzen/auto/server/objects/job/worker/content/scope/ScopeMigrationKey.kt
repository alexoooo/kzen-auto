package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.notation.GraphNotation


/**
 * The compatibility key of an [EntryScopeWorker] for a live edit (design §6.7, spike CS3): the source the
 * scope's open cursor was opened over. Computed from NOTATION alone, so the key of the edited definition is
 * known before the run is touched; an edit that changes it is refused by name before any cursor is detached
 * ([tech.kzen.auto.server.exec.job.JobLogic.refuseMigration]), and the running graph continues unedited.
 * Everything else about the scope (`entries`, its body) is compatible and applies at the next entry boundary.
 */
data class ScopeMigrationKey(
    val scope: ObjectLocation,
    val path: String
) {
    companion object {
        private val pathAttribute = AttributePath.ofName(AttributeName("path"))


        /** The key of the Worker at [location], or null when it is not an entry scope. */
        fun of(
            graphNotation: GraphNotation,
            graphDefinition: GraphDefinition,
            location: ObjectLocation
        ): ScopeMigrationKey? {
            val className = graphDefinition[location]?.className?.get()
            if (className != EntryScopeWorker::class.qualifiedName) {
                return null
            }
            return ScopeMigrationKey(location, graphNotation.getString(location, pathAttribute))
        }
    }


    /** The refusal an edit from this key to [edited] earns, or null when the open cursor can be adopted. */
    fun refusal(edited: ScopeMigrationKey): String? {
        if (edited.path == path) {
            return null
        }
        return "Source selection of ${scope.objectPath.name.value} changed. Start a new run to apply it."
    }
}
