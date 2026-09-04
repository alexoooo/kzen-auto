package tech.kzen.auto.common.data.format


object UnlimitedFormatResolutionBudget: FormatResolutionBudget {
    override suspend fun acquireColdPart(): FormatResolutionPermit = UnlimitedFormatResolutionPermit

    override fun chargeDecodedBytes(count: Int) {
        require(count >= 0) { "Decoded byte count must not be negative" }
    }
}


private object UnlimitedFormatResolutionPermit: FormatResolutionPermit {
    override fun completeSuccess() {}

    override fun close() {}
}
