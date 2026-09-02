package tech.kzen.auto.plugin.api.data

import tech.kzen.auto.common.data.read.ReadOperationalPolicy
import tech.kzen.auto.common.data.read.ReaderConfig


data class ReaderOpenRequest(
    val sourceDisplay: String,
    val part: String?,
    val config: ReaderConfig,
    val bytes: ReaderByteInput,
    val policy: ReadOperationalPolicy
)
