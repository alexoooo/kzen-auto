package tech.kzen.auto.common.objects.document.custom

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object CustomConventions {
    private val customDocumentObjectName = ObjectName("CustomDocument")

    val logicAttributeName = AttributeName("logic")
    val logicAttributePath = AttributePath.ofName(logicAttributeName)


    fun isCustomDocument(documentNotation: DocumentNotation): Boolean {
        val mainObjectNotation =
            documentNotation.objects.notations[NotationConventions.mainObjectPath]
                ?: return false

        val mainObjectIs =
            mainObjectNotation.get(NotationConventions.isAttributeName)?.asString()
                ?: return false

        return mainObjectIs == customDocumentObjectName.value
    }
}
