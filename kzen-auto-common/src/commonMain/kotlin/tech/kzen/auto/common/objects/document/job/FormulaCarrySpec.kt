package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect


/** Explicit fields retained when Formula replaces its input value. */
data class FormulaCarrySpec(
    val all: Boolean,
    val fields: List<Field>
) {
    data class Field(
        val source: String,
        val rename: String?
    )

    companion object {
        val none = FormulaCarrySpec(false, emptyList())
        val allFields = FormulaCarrySpec(true, emptyList())
    }

    init {
        require(!all || fields.isEmpty()) { "carry: all cannot also select fields" }
        require(fields.map { it.source }.distinct().size == fields.size) {
            "Formula carry fields must be unique"
        }
    }

    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            val notation = graphStructure.graphNotation.firstAttribute(objectLocation, attributeName)
            val spec = when (notation) {
                is ScalarAttributeNotation -> when (val value = notation.value) {
                    "", "none" -> none
                    "all" -> allFields
                    else -> return AttributeDefinitionAttempt.failure(
                        "Formula carry must be blank, 'none', 'all', or a source-to-rename map; found '$value'")
                }
                is MapAttributeNotation -> {
                    val fields = notation.map.map { (source, renameNotation) ->
                        val rename = renameNotation.asString()
                            ?: return AttributeDefinitionAttempt.failure(
                                "Formula carry rename for '${source.asString()}' must be a String")
                        Field(source.asString(), rename.ifBlank { null })
                    }
                    FormulaCarrySpec(false, fields)
                }
                else -> return AttributeDefinitionAttempt.failure(
                    "Formula carry must be blank, 'none', 'all', or a source-to-rename map")
            }
            return AttributeDefinitionAttempt.success(ValueAttributeDefinition(spec))
        }
    }
}
