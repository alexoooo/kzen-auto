package tech.kzen.auto.common.service

import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.GraphNotation


data class ProjectModel(
        val graphNotation: GraphNotation,
        val graphMetadata: GraphMetadata)