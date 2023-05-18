package tech.kzen.auto.server.service.v1.model

import tech.kzen.auto.server.service.v1.model.tuple.TupleDefinition


data class LogicDefinition(
    val inputs: TupleDefinition,
    val outputs: TupleDefinition,
//    val canPause: Boolean
)