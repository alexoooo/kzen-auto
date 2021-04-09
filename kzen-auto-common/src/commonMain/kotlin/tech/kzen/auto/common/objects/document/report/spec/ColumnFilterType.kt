package tech.kzen.auto.common.objects.document.report.spec


enum class ColumnFilterType {
    RequireAny,
    ExcludeAll;


    fun reject(present: Boolean): Boolean {
        when (this) {
            RequireAny ->
                if (! present) {
                    return true
                }

            ExcludeAll ->
                if (present) {
                    return true
                }
        }

        return false
    }
}