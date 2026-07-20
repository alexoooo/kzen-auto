package tech.kzen.auto.common.objects.document.registry

import tech.kzen.auto.common.objects.document.registry.spec.ClassListSpec
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.digest.Digest


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


    /**
     * Content digest of every object-registry document's declared class list (document path + sorted class names,
     * documents sorted by path). Shared by [tech.kzen.auto.server.objects.registry.ObjectRegistryDocument]'s scan
     * cache and ScriptValidationCache's key so the two can't drift. Classpath availability of a declared class is
     * process-static, so this notation-level digest fully keys a scan.
     */
    fun scanDigest(graphNotation: GraphNotation): Digest {
        return Digest.build {
            for ((path, documentNotation) in graphNotation.documents.map.entries.sortedBy { it.key.asString() }) {
                if (! isObjectRegistry(documentNotation)) {
                    continue
                }
                addDigestible(path)
                val classNames = classesSpec(documentNotation)?.classNames
                    ?: continue
                for (className in classNames.map { it.asString() }.sorted()) {
                    addUtf8(className)
                }
            }
        }
    }
}