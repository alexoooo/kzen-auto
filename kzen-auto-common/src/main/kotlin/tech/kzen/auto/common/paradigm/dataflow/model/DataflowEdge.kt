package tech.kzen.auto.common.paradigm.dataflow.model

import tech.kzen.auto.common.paradigm.dataflow.Dataflow


data class DataflowEdge(
        val from: Dataflow,
        val to: Dataflow
)