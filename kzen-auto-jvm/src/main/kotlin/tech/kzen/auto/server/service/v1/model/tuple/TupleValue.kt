package tech.kzen.auto.server.service.v1.model.tuple

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class TupleValue(
    val components: List<TupleComponentValue>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        val empty = TupleValue(listOf())

        fun ofMain(value: Any): TupleValue {
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
    fun mainComponentValue(): Any? {
        return components.find { it.name == TupleComponentName.main }?.value
    }

    fun detailComponentValue(): ExecutionValue? {
        return components.find { it.name == TupleComponentName.detail }?.value as? ExecutionValue
    }
}