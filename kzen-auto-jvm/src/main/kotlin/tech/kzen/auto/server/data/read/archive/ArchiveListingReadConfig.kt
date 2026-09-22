package tech.kzen.auto.server.data.read.archive

import tech.kzen.auto.common.data.read.ReaderConfig
import tech.kzen.lib.common.exec.MapExecutionValue


/** The archive listing has no settings: every member header is listed. */
data object ArchiveListingReadConfig: ReaderConfig {
    fun asExecutionValue(): MapExecutionValue = MapExecutionValue(emptyMap())
}
