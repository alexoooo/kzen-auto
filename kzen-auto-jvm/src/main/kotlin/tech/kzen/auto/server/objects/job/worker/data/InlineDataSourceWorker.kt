package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.data.api.DataSource
import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.datasource.DataSourceHost
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionContext
import tech.kzen.auto.server.objects.job.worker.definition.WorkerDefinitionResolution
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.exec.data.value.DataValue


/** Reader whose DataSource configuration is owned by the Worker rather than referenced as a graph object. */
abstract class InlineDataSourceWorker(
    output: ChannelOutput<DataValue>,
    emit: String,
    role: String,
    attributes: String,
    selfLocation: ObjectLocation,
    openerLookup: DataOpenerLookup,
    schemaMode: String,
    final override val hostedDataSource: DataSource,
    dataSourceCompatibilityKey: Digest
): ReadWorker(
    output, null, emit, role, attributes, selfLocation, openerLookup, schemaMode
), DataSourceHost {
    private val directResolution = WorkerDefinitionResolution.Resolved(
        selfLocation, dataSourceCompatibilityKey, hostedDataSource)


    init {
        loadSourceResolution(directResolution)
    }


    final override fun resolveSource(context: WorkerDefinitionContext): WorkerDefinitionResolution = directResolution
}
