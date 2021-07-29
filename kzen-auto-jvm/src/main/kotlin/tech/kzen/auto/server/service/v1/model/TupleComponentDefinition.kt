package tech.kzen.auto.server.service.v1.model


data class TupleComponentDefinition(
    val name: TupleComponentName,
    val type: LogicType
) {
    companion object {
        fun ofMain(type: LogicType): TupleComponentDefinition {
            return TupleComponentDefinition(
                TupleComponentName.main, type)
        }
    }
}