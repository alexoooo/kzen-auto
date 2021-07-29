package tech.kzen.auto.server.service.v1.model


data class TupleValue(
    val components: List<TupleComponentValue>
) {
    companion object {
        val empty = TupleValue(listOf())

        fun ofMain(value: Any): TupleValue {
            return TupleValue(listOf(
                TupleComponentValue.ofMain(value)
            ))
        }
    }
}