package tech.kzen.auto.client.objects.document.report.filter.model


data class ReportFilterState(
    val filterLoading: Boolean = false,
    val filterError: String? = null
)