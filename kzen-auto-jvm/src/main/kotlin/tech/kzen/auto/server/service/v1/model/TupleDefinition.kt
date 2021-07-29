package tech.kzen.auto.server.service.v1.model


data class TupleDefinition(
    val components: List<TupleComponentDefinition>
) {
    companion object {
        val empty = TupleDefinition(listOf())

        fun ofMain(type: LogicType): TupleDefinition {
            return TupleDefinition(listOf(
                TupleComponentDefinition.ofMain(type)
            ))
        }
    }
}