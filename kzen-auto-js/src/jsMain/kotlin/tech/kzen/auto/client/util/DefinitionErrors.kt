package tech.kzen.auto.client.util

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ObjectDefinitionFailure
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
// Turns the failures the client already holds (GraphDefinitionAttempt.failures, broadcast to every graph-store
// observer) into user-facing messages. A meta-declared attribute with no value, an unresolved reference, etc. fail
// the whole object definition and silently drop it from the successful graph — surfacing only later as an opaque
// "Missing: <doc>#main" at run time. These helpers let the UI show WHICH object and WHY, up front.
object DefinitionErrors {
    //-----------------------------------------------------------------------------------------------------------------
    data class Line(
        val location: ObjectLocation,
        val detail: String
    )


    //-----------------------------------------------------------------------------------------------------------------
    // Every object that failed to define, sorted by location for a stable display.
    fun all(attempt: GraphDefinitionAttempt): List<Line> {
        return attempt.failures.map.entries
            .map { (location, failure) -> Line(location, detail(failure)) }
            .sortedBy { it.location.asString() }
    }


    // Failures whose object lives in the given document (drives the per-document indicator).
    fun forDocument(attempt: GraphDefinitionAttempt, documentPath: DocumentPath): List<Line> {
        return all(attempt).filter { it.location.documentPath == documentPath }
    }


    // The reason `root` can't run — it (or a transitive dependency) failed to define — or null when it would run.
    // transitiveSuccessful is the set of objects whose full reference closure is present, which is exactly the
    // condition the server's filterTransitive(root) checks before a run.
    fun runBlocker(attempt: GraphDefinitionAttempt, root: ObjectLocation): String? {
        if (root in attempt.transitiveSuccessful.objectDefinitions) {
            return null
        }

        val directFailure = attempt.failures[root]
        return if (directFailure != null) {
            detail(directFailure)
        }
        else {
            "Depends on an object that failed to define"
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // Prefer the per-attribute errors (the actionable case — names the offending attribute, e.g.
    // "group: Unknown attribute: ..."); fall back to the object-level message when there are none.
    private fun detail(failure: ObjectDefinitionFailure): String {
        return if (failure.attributeErrors.isNotEmpty()) {
            failure.attributeErrors.entries.joinToString("; ") { (name, message) ->
                "${name.value}: $message"
            }
        }
        else {
            failure.errorMessage
        }
    }
}
