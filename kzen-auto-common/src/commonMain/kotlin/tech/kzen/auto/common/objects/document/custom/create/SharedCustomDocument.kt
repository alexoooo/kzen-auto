package tech.kzen.auto.common.objects.document.custom.create

import tech.kzen.auto.common.objects.document.custom.CustomConventions
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.obj.ObjectPathMap
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.notation.ListAttributeNotation
import tech.kzen.lib.common.model.structure.notation.ObjectNotation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.collect.persistentListOf
import tech.kzen.lib.platform.collect.persistentMapOf


/** Builds a shared Custom document containing and exporting one newly-created capability object. */
object SharedCustomDocument {
    fun objectPath(creation: CustomCreation): ObjectPath =
        NotationConventions.mainObjectPath.nest(
            CustomConventions.objectsAttributePath,
            creation.prototype.objectPath.name)


    fun create(
        customDocumentArchetype: ObjectLocation,
        creation: CustomCreation
    ): DocumentObjectNotation {
        val createdPath = objectPath(creation)
        val main = ObjectNotation.ofParent(customDocumentArchetype.toReference()).upsertAttribute(
            CustomConventions.exportsListAttributeName,
            ListAttributeNotation(persistentListOf(ScalarAttributeNotation(createdPath.asString()))))
        return DocumentObjectNotation(ObjectPathMap(persistentMapOf(
            NotationConventions.mainObjectPath to main,
            createdPath to creation.body)))
    }
}
