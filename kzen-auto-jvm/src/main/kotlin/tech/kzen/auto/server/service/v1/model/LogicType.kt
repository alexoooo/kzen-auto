package tech.kzen.auto.server.service.v1.model

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata


// TODO: support structural typing?
data class LogicType(
    val metadata: TypeMetadata
) {
    companion object {
        val string = LogicType(TypeMetadata.string)
    }
}