package tech.kzen.auto.common.data.format


interface FormatResolutionBudget {
    suspend fun acquireColdPart(): FormatResolutionPermit

    fun chargeDecodedBytes(count: Int)
}
