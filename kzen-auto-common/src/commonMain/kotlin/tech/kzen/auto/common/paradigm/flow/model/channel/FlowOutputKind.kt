package tech.kzen.auto.common.paradigm.flow.model.channel


/**
 * Which output contract a [MutableFlowOutput] was wired for — the one channel implementation backs all four
 * declared output types, so the declared type has to travel with it for the contract to be enforceable.
 */
enum class FlowOutputKind {
    Optional,
    Required,
    Batch,
    Stream
}
