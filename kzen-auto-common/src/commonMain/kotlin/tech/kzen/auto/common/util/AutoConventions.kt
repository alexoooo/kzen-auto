package tech.kzen.auto.common.util

import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.objects.document.sequence.SequenceConventions
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.GraphNotation
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.platform.DateTimeUtils


@Suppress("MemberVisibilityCanBePrivate")
object AutoConventions {
    val autoCommonDocumentNesting = DocumentNesting.parse("auto-common/")
    val autoClientDocumentNesting = DocumentNesting.parse("auto-js/")
    val autoServerDocumentNesting = DocumentNesting.parse("auto-jvm/")
    val autoMainDocumentNesting = DocumentNesting.parse("main/")

    val serverAllowed = setOf(
        NotationConventions.kzenBaseDocumentNesting,
        autoCommonDocumentNesting,
        autoServerDocumentNesting,
        autoMainDocumentNesting)

    val clientUiAllowed = setOf(
        NotationConventions.kzenBaseDocumentNesting,
        autoCommonDocumentNesting,
        autoClientDocumentNesting)


    val iconAttributePath = AttributePath.ofName(AttributeName("icon"))
    val titleAttributePath = AttributePath.ofName(AttributeName("title"))
    val descriptionAttributePath = AttributePath.ofName(AttributeName("description"))
    val displayAttributePath = AttributePath.ofName(AttributeName("display"))
    val directoryAttributePath = AttributePath.ofName(AttributeName("directory"))


    @Suppress("ConstPropertyName")
    private const val anonymousPrefix = "__ANON__"


    fun isAnonymous(objectName: ObjectName): Boolean {
        return objectName.value.startsWith(anonymousPrefix)
    }


    fun isManaged(attributeName: AttributeName): Boolean {
        return attributeName == iconAttributePath.attribute ||
            attributeName == titleAttributePath.attribute ||
            attributeName == descriptionAttributePath.attribute ||
            attributeName == displayAttributePath.attribute
    }


    fun mainDocuments(graphNotation: GraphNotation): List<DocumentPath> {
        return graphNotation
            .documents
            .values
            .keys
            .filter { it.startsWith(NotationConventions.mainDocumentNesting) }
    }


    fun randomAnonymous(): ObjectName {
        val prefix = anonymousPrefix
        val timestampSuffix = DateTimeUtils.filenameTimestamp()
        return ObjectName("$prefix$timestampSuffix")
    }


    fun isLogic(documentNotation: DocumentNotation): Boolean {
        return SequenceConventions.isSequence(documentNotation) ||
            ReportConventions.isReport(documentNotation)
    }
}