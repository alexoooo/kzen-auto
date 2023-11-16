package tech.kzen.auto.common.objects.document.data

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath


object DataFormatConventions {
    val fieldsAttributeName = AttributeName("fields")
    val fieldsAttributePath = AttributePath.ofName(fieldsAttributeName)
}