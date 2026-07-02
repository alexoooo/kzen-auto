package tech.kzen.auto.client.objects.document.job.display

import tech.kzen.auto.client.api.ReactWrapper
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName


// Base for a Worker card display: a Worker archetype names its display component in notation via the `display:`
// marker (default WorkerDisplayDefault on the base Worker), and WorkerDisplayManager resolves the matching wrapper
// by name(). Verbatim analog of ScriptStepDisplayWrapper — so a 3rd-party Worker contributes its whole card by
// registering an `is: WorkerDisplay` object, with no edit to the manager or JobController (see CC-17).
abstract class WorkerDisplayWrapper(
    private val objectLocation: ObjectLocation
):
    ReactWrapper<WorkerDisplayProps>
{
    //-----------------------------------------------------------------------------------------------------------------
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}
