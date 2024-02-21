package tech.kzen.auto.client.objects.document.report.run.model


data class ReportRunState(
//    val logicStatus: LogicStatus? = null,
//    val otherRunning: Boolean = false,

    val progress: ReportRunProgress? = null,

//    val starting: Boolean = false,
//    val cancelling: Boolean = false,

    val runError: String? = null
) {
//    fun submitting(): Boolean {
//        return starting || cancelling
//    }
}
