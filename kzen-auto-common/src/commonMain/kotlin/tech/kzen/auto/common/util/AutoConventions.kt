package tech.kzen.auto.common.util

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentNesting
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
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
    val groupAttributePath = AttributePath.ofName(AttributeName("group"))

    // Declared "false" on an archetype to opt its instances out of server-side instance reuse
    // (see GraphInstanceCache) - the escape hatch for an action that can't honour the
    // statelessness contract of DetachedAction / DetachedDownloadAction / ManagedTask.
    val instanceCachingAttributePath = AttributePath.ofName(AttributeName("instanceCaching"))


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
            .map
            .keys
            .filter { it.startsWith(NotationConventions.mainDocumentNesting) }
    }


    fun randomAnonymous(): ObjectName {
        val prefix = anonymousPrefix
        val timestampSuffix = DateTimeUtils.filenameTimestamp()
        return ObjectName("$prefix$timestampSuffix")
    }


    val logicObjectName = ObjectName("Logic")


    // True when the document's `main` is a runnable Logic (Script / Flow / Job / Report, or a 3rd-party
    // paradigm) — tested by the main archetype's inheritance chain reaching the common `Logic` marker, so a new
    // paradigm is recognized without editing this method (see CC-17). The server-side analogue is the
    // LogicDocument interface each paradigm's `main` archetype implements.
    fun isLogic(graphNotation: GraphNotation, documentPath: DocumentPath): Boolean {
        val mainLocation = ObjectLocation(documentPath, NotationConventions.mainObjectPath)
        if (mainLocation !in graphNotation.coalesce) {
            return false
        }
        return graphNotation.inheritanceChain(mainLocation).any {
            it.objectPath.name == logicObjectName
        }
    }
}