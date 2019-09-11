package tech.kzen.auto.common.util

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions


object AutoConventions {
    val iconAttributePath = AttributePath.ofName(AttributeName("icon"))
    val titleAttributePath = AttributePath.ofName(AttributeName("title"))
    val descriptionAttributePath = AttributePath.ofName(AttributeName("description"))
    val displayAttributePath = AttributePath.ofName(AttributeName("display"))
    val directoryAttributePath = AttributePath.ofName(AttributeName("directory"))


    const val anonymousPrefix = "__ANON__"


    fun isAnonymous(objectName: ObjectName): Boolean {
        return objectName.value.startsWith(anonymousPrefix)
    }


    fun isManaged(attributeName: AttributeName): Boolean {
        return attributeName == iconAttributePath.attribute ||
                attributeName == titleAttributePath.attribute ||
                attributeName == descriptionAttributePath.attribute ||
                attributeName == displayAttributePath.attribute
    }


//    fun isManaged(attributePath: AttributePath): Boolean {
//        return attributePath == iconAttributePath ||
//                attributePath == titleAttributePath ||
//                attributePath == descriptionAttributePath
//    }


    fun mainDocuments(graphNotation: GraphNotation): List<DocumentPath> {
        return graphNotation
                .documents
                .values
                .keys
                .filter { it.startsWith(NotationConventions.mainDocumentNesting) }
    }
}