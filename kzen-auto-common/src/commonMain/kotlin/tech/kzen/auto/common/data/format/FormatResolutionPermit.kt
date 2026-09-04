package tech.kzen.auto.common.data.format


interface FormatResolutionPermit: AutoCloseable {
    fun completeSuccess()

    override fun close()
}
