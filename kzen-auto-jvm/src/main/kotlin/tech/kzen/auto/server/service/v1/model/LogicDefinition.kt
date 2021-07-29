package tech.kzen.auto.server.service.v1.model


data class LogicDefinition(
    val inputs: TupleDefinition,
    val outputs: TupleDefinition,
//    val canPause: Boolean
)