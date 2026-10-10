package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.model.attribute.AttributeName


/**
 * Keys of what a Worker's validation says about one of its fields before Run
 * ([tech.kzen.auto.common.objects.document.logic.StepValidation.details]), read by the field's editor whatever the
 * Worker: the value a blank field stands for, and the type of a Kotlin expression's value (a
 * [tech.kzen.lib.common.exec.data.type.DataContract] as its execution value).
 */
object JobFieldConventions {
    fun defaultKey(attributeName: AttributeName): String =
        "${attributeName.value}.default"


    fun typeKey(attributeName: AttributeName): String =
        "${attributeName.value}.type"
}
