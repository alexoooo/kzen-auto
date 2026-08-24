package tech.kzen.auto.common.data

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object DataSourceConventions {
    private const val dataSourceClassName = "tech.kzen.auto.common.data.api.DataSource"

    val dataSourceObjectName = ObjectName("DataSource")
    val shapeProviderObjectName = ObjectName("DataSourceShapeProvider")

    // DataSourceShapeProvider is capability metadata, while these effective fields are its fail-closed
    // Read-lane projection contract. A third-party provider that cannot expose them is not advertised.
    val shapeSourceAttributeName = AttributeName("source")
    val shapeEmitAttributeName = AttributeName("emit")
    val shapeRoleAttributeName = AttributeName("role")
    val shapeAttributesAttributeName = AttributeName("attributes")
    val shapeSchemaModeAttributeName = AttributeName("schemaMode")

    val sourcesAttributeName = AttributeName("sources")
    val sourcesAttributePath = AttributePath.ofName(sourcesAttributeName)

    val dataSourceActionsLocation = ObjectLocation.parse(
        "auto-jvm/datasource/data-source-jvm.yaml#DataSourceActions")

    const val sourceParameter = "source"
    const val actionParameter = "action"
    const val partParameter = "part"
    const val resolveAction = "resolve"
    const val shapeAction = "shape"


    fun isDataSource(graphNotation: GraphNotation, location: ObjectLocation): Boolean {
        if (location !in graphNotation.coalesce) {
            return false
        }
        return graphNotation
            .inheritanceChain(location)
            .drop(1)
            .any { it.objectPath.name == dataSourceObjectName }
    }


    fun isDataSourceType(type: TypeMetadata?): Boolean {
        return type?.className?.asString() == dataSourceClassName
    }


    fun isShapeProvider(graphNotation: GraphNotation, location: ObjectLocation): Boolean {
        return location in graphNotation.coalesce && graphNotation
            .inheritanceChain(location)
            .any { it.objectPath.name == shapeProviderObjectName }
    }


    fun allDataSources(graphNotation: GraphNotation): List<ObjectLocation> {
        return graphNotation.coalesce.map.keys.filter {
            isDataSource(graphNotation, it) && graphNotation
                .directAttribute(it, NotationConventions.abstractAttributePath)
                ?.asBoolean() != true
        }.sortedBy { it.asString() }
    }
}
