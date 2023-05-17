package tech.kzen.auto.server.service.v1.model

import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


// TODO: support structural typing?
data class LogicType(
    val metadata: TypeMetadata
) {
    companion object {
        val any = LogicType(TypeMetadata.any)
        val string = LogicType(TypeMetadata.string)
        val boolean = LogicType(TypeMetadata.boolean)
//        val int = LogicType(TypeMetadata.int)
        val executionValue = LogicType(ExecutionValue.typeMetadata)
    }
}