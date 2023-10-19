package tech.kzen.auto.server.service.v1.model.tuple

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class TupleValue(
    val components: List<TupleComponentValue>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = TupleValue(listOf())

        fun ofMain(value: Any?): TupleValue {
            return TupleValue(listOf(
                TupleComponentValue.ofMain(value)
            ))
        }

        fun ofVoidWithDetail(value: ExecutionValue): TupleValue {
            return TupleValue(listOf(
                TupleComponentValue.ofDetail(value)
            ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun find(tupleComponentName: TupleComponentName): Any? {
        return components.find { it.name == tupleComponentName }?.value
    }

    fun mainComponentValue(): Any? {
        return find(TupleComponentName.main)
    }

    fun detailComponentValue(): ExecutionValue? {
        return find(TupleComponentName.detail) as? ExecutionValue
    }
}