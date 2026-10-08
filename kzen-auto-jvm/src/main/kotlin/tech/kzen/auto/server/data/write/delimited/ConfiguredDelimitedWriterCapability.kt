package tech.kzen.auto.server.data.write.delimited

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.server.data.write.RecordEncoder
import tech.kzen.auto.server.data.write.RecordWriterCapability
import tech.kzen.auto.server.objects.datasource.format.ConfiguredDelimitedFormat


object ConfiguredDelimitedWriterCapability: RecordWriterCapability {
    override val identity = "tech.kzen.auto/configured-delimited-writer-v1"

    override fun encoder(format: ConfiguredRecordFormat, columns: List<String>): RecordEncoder {
        val delimited = format as? ConfiguredDelimitedFormat
            ?: throw IllegalArgumentException("${format.title} is not a configured delimited format")
        return DelimitedRecordEncoder(delimited.title, columns, delimited.baseConfig)
    }
}
