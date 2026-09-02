package tech.kzen.auto.common.objects.document.custom.create

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.ObjectNotation


data class CustomCreation(
    val prototype: ObjectLocation,
    val category: String,
    val label: String,
    val body: ObjectNotation
) {
    companion object {
        val customCreatableObjectName = ObjectName("CustomCreatable")
        val customCreateAttributeName = AttributeName("customCreate")
    }
}
