package tech.kzen.auto.client.objects.document.common.attribute

import tech.kzen.auto.client.util.async
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.service.context.GraphDefiner
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.metadata.NotationMetadataReader
import tech.kzen.lib.common.service.notation.NotationReducer
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.service.store.DirectGraphStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull


class AttributeWrapperLookupTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val mainPath = DocumentPath.parse("main.yaml")
    private val widget = ObjectLocation(mainPath, ObjectPath.parse("Widget"))

    // `direct` declares its editor inline; `inherited` declares none but is typed as ClosePolicyLike, whose
    // type-level `meta.ref` map carries one - the inheritance path SelectValuesEditor rides in production
    // (ResourceClosePolicy -> every closePolicy attribute), and the reason this fixture exists.
    private val seedNotation = """
Text:
  class: kotlin.String

ClosePolicyLike:
  abstract: true
  class: kotlin.Any
  meta:
    ref:
      editor: SelectValuesEditor

Widget:
  class: kotlin.Any
  meta:
    direct:
      is: Text
      editor: TextAttributeEditor
      summary: TextAttributeView
    inherited: ClosePolicyLike
    plain:
      is: Text
    blank:
      is: Text
      editor: ""
"""


    //-----------------------------------------------------------------------------------------------------------------
    private suspend fun graphStructure(): GraphStructure {
        val media = MapNotationMedia()
        media.writeDocument(mainPath, seedNotation)
        return DirectGraphStore(
            media, YamlNotationParser(), NotationMetadataReader(), GraphDefiner, NotationReducer()
        ).graphStructure()
    }


    private suspend fun wrapperName(attribute: String, metadataKey: AttributePath): ObjectName? {
        return AttributeWrapperLookup.wrapperName(
            graphStructure(), widget, AttributeName(attribute), metadataKey)
    }


    private suspend fun editorName(attribute: String): ObjectName? {
        return wrapperName(attribute, AttributeWrapperLookup.editorAttributePath)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun directKeyNamesTheEditor() = async {
        assertEquals(ObjectName("TextAttributeEditor"), editorName("direct"))
    }


    @Test
    fun typeLevelRefKeyIsInherited() = async {
        assertEquals(ObjectName("SelectValuesEditor"), editorName("inherited"))
    }


    @Test
    fun absentKeyIsNull() = async {
        assertNull(editorName("plain"))
    }


    @Test
    fun blankKeyIsNull() = async {
        assertNull(editorName("blank"))
    }


    @Test
    fun unknownAttributeIsNull() = async {
        assertNull(editorName("noSuchAttribute"))
    }


    // The key is a parameter, not a branch: the same lookup serves AttributeViewManager's `summary:`.
    @Test
    fun summaryKeyNamesTheView() = async {
        assertEquals(
            ObjectName("TextAttributeView"),
            wrapperName("direct", AttributeWrapperLookup.summaryAttributePath))
        assertNull(wrapperName("plain", AttributeWrapperLookup.summaryAttributePath))
    }
}
