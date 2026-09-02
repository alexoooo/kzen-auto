package tech.kzen.auto.client.objects.document.job.source

import tech.kzen.auto.client.objects.document.job.display.DataContractDisplay


internal object DataSourceInspectionDisplay {
    fun of(
        resolve: DataSourceResolveStore.State?,
        shape: DataSourceShapeStore.State?,
        pending: Boolean
    ): DataContractDisplay {
        val loading = resolve?.resolving == true ||
                shape?.parts?.values?.any { it.inspecting } == true || pending
        val error = resolve?.error
            ?: shape?.parts?.values?.firstNotNullOfOrNull { it.error }
        return DataContractDisplay.of(shape?.aggregate, loading, error)
    }
}
