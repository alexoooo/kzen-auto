package tech.kzen.auto.server.data.format


class SourceFormatResolutionBudgetFactory(
    private val policy: SourceFormatResolutionPolicy = SourceFormatResolutionPolicy.default
) {
    fun create(): SourceFormatResolutionBudget = SourceFormatResolutionBudget(policy)
}
