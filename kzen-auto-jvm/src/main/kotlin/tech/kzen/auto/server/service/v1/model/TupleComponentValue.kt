package tech.kzen.auto.server.service.v1.model


data class TupleComponentValue(
    val name: TupleComponentName,
    val value: Any
) {
    companion object {
        fun ofMain(value: Any): TupleComponentValue {
            return TupleComponentValue(
                TupleComponentName.main, value)
        }
    }
}