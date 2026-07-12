package tech.kzen.auto.common.objects.document.target

import tech.kzen.lib.common.model.definition.AttributeDefinition
import tech.kzen.lib.common.model.definition.ReferenceAttributeDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ObjectReferenceHost
import tech.kzen.lib.common.reflect.Reflect


//---------------------------------------------------------------------------------------------------------------------
@Reflect
class FocusTargetSpecType: TargetSpecType() {
    override val typeName = "Focus"


    override fun createSpec(
        valueDefinition: AttributeDefinition?,
        policy: TargetMatchPolicy,
        objectLocation: ObjectLocation,
        partialGraphInstance: GraphInstance
    ): TargetSpec {
        return FocusTarget
    }
}


//---------------------------------------------------------------------------------------------------------------------
@Reflect
class TextTargetSpecType: TargetSpecType() {
    override val typeName = "Text"


    override fun createSpec(
        valueDefinition: AttributeDefinition?,
        policy: TargetMatchPolicy,
        objectLocation: ObjectLocation,
        partialGraphInstance: GraphInstance
    ): TargetSpec {
        return TextTarget(stringValue(valueDefinition), policy)
    }
}


//---------------------------------------------------------------------------------------------------------------------
@Reflect
class XpathTargetSpecType: TargetSpecType() {
    override val typeName = "Xpath"


    override fun createSpec(
        valueDefinition: AttributeDefinition?,
        policy: TargetMatchPolicy,
        objectLocation: ObjectLocation,
        partialGraphInstance: GraphInstance
    ): TargetSpec {
        return XpathTarget(stringValue(valueDefinition), policy)
    }
}


//---------------------------------------------------------------------------------------------------------------------
/**
 * References a Target document (`valueKind: reference`): the created spec holds the resolved
 * [TargetDocument] instance (crops + tolerance).
 */
@Reflect
class VisualTargetSpecType: TargetSpecType() {
    override val typeName = "Visual"


    override fun createSpec(
        valueDefinition: AttributeDefinition?,
        policy: TargetMatchPolicy,
        objectLocation: ObjectLocation,
        partialGraphInstance: GraphInstance
    ): TargetSpec {
        val reference = (valueDefinition as ReferenceAttributeDefinition).objectReference!!

        val location = partialGraphInstance.objectInstances.locate(
            reference, ObjectReferenceHost.ofLocation(objectLocation))

        val targetDocument =
            partialGraphInstance[location]?.reference as TargetDocument

        return VisualTarget(targetDocument, policy)
    }
}


//---------------------------------------------------------------------------------------------------------------------
private fun stringValue(valueDefinition: AttributeDefinition?): String {
    return (valueDefinition as ValueAttributeDefinition).value as String
}
