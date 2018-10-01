package tech.kzen.auto.client.objects.action

import react.RBuilder
import react.ReactElement
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation


interface ActionWrapper {
    fun priority(): Int


    fun isApplicableTo(
            objectName: String,
            projectNotation: ProjectNotation,
            graphMetadata: GraphMetadata
    ): Boolean


    fun render(
            rBuilder: RBuilder,

            objectName: String,

            projectNotation: ProjectNotation,
            graphMetadata: GraphMetadata,

            executionStatus: ExecutionStatus?,
            nextToExecute: Boolean
    ): ReactElement
}