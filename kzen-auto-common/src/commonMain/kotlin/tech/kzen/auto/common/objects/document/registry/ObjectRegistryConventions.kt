package tech.kzen.auto.common.objects.document.registry

import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object ObjectRegistryConventions {
    val objectName = ObjectName("ObjectRegistry")

    val classesAttributeName = AttributeName("classes")
    val classesAttributePath = AttributePath.ofName(classesAttributeName)


    fun isObjectRegistry(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
            ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
            ?: return false

        return mainObjectIs == objectName.value
    }


    fun classesSpec(documentNotation: DocumentNotation): ClassListSpec? {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
            ?: return null

        val untypedClassesAttributeNotation = mainObjectNotation.get(classesAttributeName)
            ?: ListAttributeNotation.empty

        val classesAttributeNotation = untypedClassesAttributeNotation as? ListAttributeNotation
            ?: return null

        return ClassListSpec.ofAttributeNotation(classesAttributeNotation)
    }
}