package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.exec.job.RunResourceControl
import tech.kzen.lib.common.exec.engine.resource.ResourceRegistry


/**
 * The run's resource registry behind a framework Worker's [JobControl], for the framework's own holders (the
 * source ingress, a pull loop). Null outside a run (a test control), where nothing is adopted and a pulled
 * closeable is the source's own. Resolved by the control's declared [RunResourceControl] capability, never its
 * concrete type (CC-17).
 */
internal fun JobControl.resources(): ResourceRegistry? =
    (this as? RunResourceControl)?.resources
