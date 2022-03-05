package tech.kzen.auto.server.service.v1.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class TupleComponentValue(
    val name: TupleComponentName,
    val value: Any
) {
    companion object {
        fun ofMain(value: Any): TupleComponentValue {
            return TupleComponentValue(
                TupleComponentName.main, value)
        }


        fun ofDetail(value: ExecutionValue): TupleComponentValue {
            return TupleComponentValue(
                TupleComponentName.detail, value)
        }
    }
}