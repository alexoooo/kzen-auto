package tech.kzen.auto.server.objects.job

import tech.kzen.auto.common.data.DataSourceConventions
import tech.kzen.auto.server.objects.logic.LogicValidationDigest
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReference
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.util.digest.Digest


/**
 * Widens a Job validation key with every nominal dependency declared through a DataSource-typed capability.
 * This deliberately knows neither ReadWorker nor a concrete source class. A source's structural dependencies
 * (including DataSchema) enter through that source location's ordinary definition closure.
 */
object JobValidationDigest {
    fun documentClosureKey(
        documentPath: DocumentPath,
        graphDefinition: GraphDefinition
    ): Digest? {
        val base = LogicValidationDigest.documentClosureKey(documentPath, graphDefinition)
            ?: return null
        return try {
            val graphStructure = graphDefinition.graphStructure
            val graphNotation = graphStructure.graphNotation
            val document = graphNotation.documents[documentPath]
                ?: return base
            val dependencies = linkedSetOf<ObjectLocation>()

            for (objectPath in document.objects.notations.map.keys) {
                val host = ObjectLocation(documentPath, objectPath)
                if (!DataSourceConventions.isShapeProvider(graphNotation, host)) {
                    continue
                }
                val metadata = graphStructure.graphMetadata.objectMetadata.map[host]
                    ?: continue
                for ((attributeName, attributeMetadata) in metadata.attributes.map) {
                    if (!DataSourceConventions.isDataSourceType(attributeMetadata.type)) {
                        continue
                    }
                    val value = graphNotation.firstAttribute(host, attributeName).asString()
                    if (value.isNullOrBlank()) {
                        continue
                    }
                    val target = graphNotation.coalesce.locateOptional(
                        ObjectReference.parse(value), ObjectReferenceHost.ofLocation(host))
                        ?: continue
                    dependencies.add(target)
                }
            }

            Digest.build {
                addDigest(base)
                for (dependency in dependencies.sortedBy { it.asString() }) {
                    addDigestible(graphDefinition.transitiveDigest(listOf(dependency)))
                }
            }
        }
        catch (_: Exception) {
            null
        }
    }
}
