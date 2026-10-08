package tech.kzen.auto.server.data.write

import tech.kzen.auto.common.data.format.ConfiguredRecordFormat


/** Writes the formats whose [ConfiguredRecordFormat.writerCapabilityIdentity] is its [identity]. */
interface RecordWriterCapability {
    val identity: String

    /**
     * An encoder of records with [columns] in [format]. Fails by name when the format cannot write those columns at
     * all (e.g. they differ from its schema), so creating one is also the check made before any record exists.
     */
    fun encoder(format: ConfiguredRecordFormat, columns: List<String>): RecordEncoder
}
