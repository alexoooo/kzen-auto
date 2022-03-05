package tech.kzen.auto.server.service.v1.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue


data class TupleComponentDefinition(
    val name: TupleComponentName,
    val type: LogicType
) {
    companion object {
        fun ofMain(type: LogicType): TupleComponentDefinition {
            return TupleComponentDefinition(
                TupleComponentName.main, type)
        }


        fun ofDetail(): TupleComponentDefinition {
            return TupleComponentDefinition(
                TupleComponentName.detail,
                LogicType(ExecutionValue.typeMetadata)
            )
        }
    }
}