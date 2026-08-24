package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.data.schema.DataSchemaDocument
import tech.kzen.auto.server.objects.datasource.LogicDataSource
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest


@Reflect
class LogicSourceWorker(
    output: ChannelOutput<Any?>,
    instructions: ObjectLocation?,
    arguments: List<String>,
    schema: DataSchemaDocument?,
    emit: String,
    role: String,
    attributes: String,
    selfLocation: ObjectLocation,
    @Service openerLookup: DataOpenerLookup,
    schemaMode: String = DataReadCore.schemaSuperset
): InlineDataSourceWorker(
    output, emit, role, attributes, selfLocation, openerLookup, schemaMode,
    LogicDataSource(instructions, arguments, schema),
    compatibilityKey(instructions, arguments, schema)
) {
    companion object {
        internal fun compatibilityKey(
            instructions: ObjectLocation?,
            arguments: List<String>,
            schema: DataSchemaDocument?
        ): Digest = Digest.build {
            addDigestibleNullable(instructions)
            arguments.forEach(::addUtf8)
            addDigestibleNullable(schema?.shape()?.header)
        }
    }
}
