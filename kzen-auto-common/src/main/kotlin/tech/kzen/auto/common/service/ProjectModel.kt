package tech.kzen.auto.common.service

import tech.kzen.lib.common.metadata.model.GraphMetadata
import tech.kzen.lib.common.notation.model.ProjectNotation


data class ProjectModel(
        val projectNotation: ProjectNotation,
        val graphMetadata: GraphMetadata)