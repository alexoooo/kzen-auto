package tech.kzen.auto.common.objects.document.data.spec

import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.ClassName
import tech.kzen.lib.platform.collect.persistentMapOf
import tech.kzen.lib.platform.collect.toPersistentList


data class FieldFormatSpec(
//    val fieldName: String,
//    val fieldHeader: String
    val typeMetadata: TypeMetadata
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
//        val nameAttributeSegment = AttributeSegment.ofKey("name")
//        val headerAttributeSegment = AttributeSegment.ofKey("header")

        fun ofNotation(attributeNotation: MapAttributeNotation): FieldFormatSpec {
            val typeMetadata = readTypeMetadata(attributeNotation)
            return FieldFormatSpec(typeMetadata)
        }


        private fun readTypeMetadata(attributeNotation: MapAttributeNotation): TypeMetadata {
            val className = attributeNotation
                .values[NotationConventions.classAttributeSegment]
                ?.asString()
                ?: throw IllegalArgumentException("Class name expected: $attributeNotation")

            val nestedGenericsNotation = attributeNotation
                .values[NotationConventions.ofAttributeSegment] as? ListAttributeNotation
                ?: throw IllegalArgumentException("Generics expected: $attributeNotation")

            val nestedGenerics: List<TypeMetadata> =
                nestedGenericsNotation.values.map { readTypeMetadata(it as MapAttributeNotation) }

            val nullable = attributeNotation
                .values[NotationConventions.nullableAttributeSegment]
                ?.asBoolean()
                ?: throw IllegalArgumentException("Nullable info expected: $attributeNotation")

            return TypeMetadata(
                ClassName(className),
                nestedGenerics,
                nullable)
        }


        private fun asNotation(typeMetadata: TypeMetadata): MapAttributeNotation {
            return MapAttributeNotation(persistentMapOf(
                NotationConventions.classAttributeSegment to
                    ScalarAttributeNotation(typeMetadata.className.asString()),

                NotationConventions.ofAttributeSegment to
                    ListAttributeNotation(
                        typeMetadata.generics.map { asNotation(it) }.toPersistentList()
                    ),

                NotationConventions.nullableAttributeSegment to
                    ScalarAttributeNotation(typeMetadata.nullable.toString())
            ))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asNotation(): MapAttributeNotation {
        return asNotation(typeMetadata)
    }
}