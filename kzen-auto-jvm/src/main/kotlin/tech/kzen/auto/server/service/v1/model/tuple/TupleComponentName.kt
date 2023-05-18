package tech.kzen.auto.server.service.v1.model.tuple


data class TupleComponentName(
    val value: String
) {
    companion object {
        val main = TupleComponentName("main")
        val detail = TupleComponentName("detail")
    }
}