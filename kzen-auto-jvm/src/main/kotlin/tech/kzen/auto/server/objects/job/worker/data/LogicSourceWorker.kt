package tech.kzen.auto.server.objects.job.worker.data

import tech.kzen.auto.common.paradigm.job.api.ChannelOutput
import tech.kzen.auto.common.data.schema.RecordSchema
import tech.kzen.auto.common.data.schema.declaredShape
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.datasource.LogicDataSource
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.exec.data.value.DataValue


@Reflect
class LogicSourceWorker(
    output: ChannelOutput<DataValue>,
    instructions: ObjectLocation?,
    arguments: List<String>,
    schema: RecordSchema?,
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
            schema: RecordSchema?
        ): Digest = Digest.build {
            addDigestibleNullable(instructions)
            arguments.forEach(::addUtf8)
            addDigestibleNullable(schema?.declaredShape()?.asExecutionValue())
        }
    }
}
