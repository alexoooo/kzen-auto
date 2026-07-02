package tech.kzen.auto.client.objects.document.job

import tech.kzen.auto.common.objects.document.job.JobChannelDerivation
import tech.kzen.auto.common.objects.document.job.JobConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


/**
 * The name of the (external duplex) `serve` channel to address for a Worker's pull query. When the serve port is
 * auto-managed (open — blank, or a dangling leftover the editor hides), it is the deterministic auto-synthesized
 * name; only a real manual `serve` reference that resolves to an existing Channel names the channel itself. Routing
 * every pull through the same [JobChannelDerivation] keeps this in step with the server's synthesis. Shared by the
 * preview-slice ([tech.kzen.auto.client.objects.document.job.display.PreviewWorkerDisplay]) and summary
 * ([tech.kzen.auto.client.objects.document.job.display.SummaryWorkerDisplay]) pulls so the two can't drift.
 */
object JobServeChannelResolver {
    fun serveChannelName(graphStructure: GraphStructure, workerLocation: ObjectLocation): String {
        val autoManagedServe = JobChannelDerivation
            .derive(graphStructure, workerLocation.documentPath)
            .serves
            .any { it.worker == workerLocation }
        return if (autoManagedServe) {
            JobConventions.autoServeChannelName(workerLocation.objectPath)
        }
        else {
            graphStructure.graphNotation
                .firstAttribute(workerLocation, AttributePath.ofName(AttributeName("serve")))
                ?.asString()
                ?.substringAfterLast("/")
                ?: JobConventions.autoServeChannelName(workerLocation.objectPath)
        }
    }
}
