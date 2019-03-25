package tech.kzen.auto.common.objects.document

import tech.kzen.lib.common.api.model.ObjectLocation
import tech.kzen.lib.common.api.model.ObjectName


abstract class DocumentArchetype(
        private val objectLocation: ObjectLocation
) {
    fun name(): ObjectName {
        return objectLocation.objectPath.name
    }
}