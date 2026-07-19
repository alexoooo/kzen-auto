package tech.kzen.auto.client.util

import tech.kzen.lib.common.model.definition.GraphDefinitionAttempt
import tech.kzen.lib.common.model.definition.ObjectDefinitionFailure
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation


//---------------------------------------------------------------------------------------------------------------------
// Turns the failures the client already holds (broadcast to every graph-store observer) into user-facing messages,
// so the UI can show WHICH object and WHY instead of an opaque "Missing: <doc>#main" at run time.
//
// Two distinct absences, and they are NOT interchangeable:
//  - failed to define (GraphDefinitionAttempt.failures) — e.g. a meta-declared attribute with no value. Always
//    broken; safe to report unprompted. This is what all()/forDocument() list.
//  - pruned from the successful graph (transitiveFailures) — a reference that dangles or is required-but-empty.
//    Usually broken, but NOT always: see the note on all(). Only reported when something is actually blocked,
//    which is runBlocker's job.
object DefinitionErrors {
    //-----------------------------------------------------------------------------------------------------------------
    data class Line(
        val location: ObjectLocation,
        val detail: String
    )


    //-----------------------------------------------------------------------------------------------------------------
    // Every object that failed to DEFINE, sorted by location for a stable display.
    //
    // Deliberately NOT attempt.transitiveFailures: absence from the successful graph is not the same thing as
    // broken notation. A Job worker's channel ports are declared blank on purpose (`input: ""` / `serve: ""` in
    // job-worker.yaml, non-nullable) and are filled only in JobChannelSynthesis's ephemeral run-copy, so every
    // saved Job worker is pruned as "Required reference is empty" while running perfectly. Listing pruned objects
    // here flagged three false errors per Job document — and, via StageController's panel, claimed they couldn't
    // run. Use runBlocker for the pruned case: it is asked about one specific run root, where absence really does
    // block, and it consults transitiveFailures to name the cause precisely.
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

        // Follow missingObjects toward the root cause, naming the blocking object when it isn't the root itself.
        // Terminates on any graph: each hop is to a not-yet-visited location, and a cycle simply runs out of them.
        var location = root
        val visited = mutableSetOf<ObjectLocation>()

        while (true) {
            visited.add(location)

            val failure = attempt.transitiveFailures[location]
                ?: return "Failed to define"

            val next = failure.missingObjects.values.firstOrNull { it !in visited }
            if (next == null || location in attempt.failures) {
                return when (location) {
                    root -> detail(failure)
                    else -> "Blocked by $location: ${detail(failure)}"
                }
            }

            location = next
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
