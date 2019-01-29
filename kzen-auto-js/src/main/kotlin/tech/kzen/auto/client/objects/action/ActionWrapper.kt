package tech.kzen.auto.client.objects.action

import react.RBuilder
import react.ReactElement
import tech.kzen.auto.common.exec.ExecutionStatus
import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectPath
import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation


interface ActionWrapper {
    fun priority(): Int


    fun isApplicableTo(
            objectName: ObjectPath,
            projectNotation: GraphNotation,
            graphMetadata: GraphMetadata
    ): Boolean


    fun render(
            rBuilder: RBuilder,

            objectLocation: ObjectLocation,

            projectNotation: GraphNotation,
            graphMetadata: GraphMetadata,

            executionStatus: ExecutionStatus?,
            nextToExecute: Boolean
    ): ReactElement
}