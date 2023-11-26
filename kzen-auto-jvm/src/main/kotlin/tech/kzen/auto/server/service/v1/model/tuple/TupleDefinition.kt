package tech.kzen.auto.server.service.v1.model.tuple

import tech.kzen.auto.server.service.v1.model.LogicType


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


        fun ofVoidWithDetail(): TupleDefinition {
            return TupleDefinition(listOf(
                TupleComponentDefinition.ofDetail()
            ))
        }
    }


    fun find(name: TupleComponentName): LogicType? {
        return components
            .find { it.name == name }
            ?.type
    }
}