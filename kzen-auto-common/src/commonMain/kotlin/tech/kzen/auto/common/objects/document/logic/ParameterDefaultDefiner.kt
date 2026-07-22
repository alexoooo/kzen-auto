package tech.kzen.auto.common.objects.document.logic

import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.TypeMetadata
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassNames


// Coerces a parameter declaration's optional `default` notation scalar into a typed runtime value, keyed on
// the sibling `type` (a TypeMetadata) parsed by TypeMetadataDefiner — shared by the Script and Job `parameters`
// branches. Mirrors StructuralAttributeDefiner's scalar coercion, but lenient: an absent/non-scalar default, an
// unsupported type (Any / List / Set / object refs), or text that fails to parse all yield a null default rather
// than failing the whole document definition — the field is edited live with debounced commits, so transient
// unparseable text must not break validation. The typed value is the fallback used when a run supplies no
// argument for the parameter (ScriptLogic's binding seed; EngineJobControl.parameter).
@Reflect
class ParameterDefaultDefiner: AttributeDefiner {
    companion object {
        private val typeAttributePath = AttributePath.ofName(AttributeName("type"))
        private val defaultAttributePath = AttributePath.ofName(AttributeName("default"))


        // Resolve a parameter declaration's default straight from notation: the `default` scalar coerced by the
        // sibling `type`. Shared by the Script and Job compilers so both resolve defaults identically.
        fun resolve(location: ObjectLocation, graphNotation: GraphNotation): Any? {
            val defaultText = (graphNotation.firstAttribute(location, defaultAttributePath)
                as? ScalarAttributeNotation)
                ?.value
                ?: return null
            val type = graphNotation.firstAttribute(location, typeAttributePath)
                ?.let { TypeMetadataDefiner.parse(it) }
                ?: return null
            return coerce(defaultText, type)
        }


        // Coerce a default's notation text into a typed value keyed on the parameter's declared type.
        fun coerce(text: String, type: TypeMetadata): Any? {
            return when (type.className) {
                ClassNames.kotlinString -> text
                ClassNames.kotlinBoolean -> text.toBooleanStrictOrNull()
                ClassNames.kotlinInt -> text.toIntOrNull()
                ClassNames.kotlinLong -> text.toLongOrNull()
                ClassNames.kotlinDouble -> text.toDoubleOrNull()
                else -> null
            }
        }
    }


    override fun define(
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        graphStructure: GraphStructure,
        partialGraphDefinition: GraphDefinition,
        partialGraphInstance: GraphInstance
    ): AttributeDefinitionAttempt {
        // NB: the AttributePath overload returns null for an absent attribute; the AttributeName overload
        //     throws. `default` is optional, so both lookups must use the nullable form.
        val defaultNotation = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, AttributePath.ofName(attributeName)) as? ScalarAttributeNotation
            ?: return AttributeDefinitionAttempt.success(ValueAttributeDefinition(null))

        val typeMetadata = graphStructure
            .graphNotation
            .firstAttribute(objectLocation, typeAttributePath)
            ?.let { TypeMetadataDefiner.parse(it) }
            ?: return AttributeDefinitionAttempt.success(ValueAttributeDefinition(null))

        return AttributeDefinitionAttempt.success(
            ValueAttributeDefinition(coerce(defaultNotation.value, typeMetadata)))
    }
}
