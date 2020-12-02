package tech.kzen.auto.common.objects.document.report.output


enum class OutputStatus {
    Missing,
    Running,
    Done,
    Cancelled,
    Failed,
    Killed,
    Corrupt;


    fun isTerminal(): Boolean {
        return this != Missing &&
                this != Running
    }
}