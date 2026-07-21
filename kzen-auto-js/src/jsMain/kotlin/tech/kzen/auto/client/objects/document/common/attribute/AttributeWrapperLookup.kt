package tech.kzen.auto.client.objects.document.common.attribute

import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectName
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.metadata.AttributeMetadata


// The notation-driven dispatch convention behind AttributeEditorManager (`editor:`) and AttributeViewManager
// (`summary:`): an attribute's metadata names the wrapper object that renders it, and the manager resolves that
// name against its autowired list. This object owns both the keys and the read, so the string contract - the
// open-set third-party-extension seam - has a single home.
//
// The name arrives through the same metadata inheritance the reader applies to `by:` / `is:`: a type-level
// `meta.ref` map propagates the key to every attribute declared `is: <that type>` (see NotationMetadataReader's
// resolveMetadataRef), which is how ResourceClosePolicy hands SelectValuesEditor to every closePolicy attribute.
object AttributeWrapperLookup {
    //-----------------------------------------------------------------------------------------------------------------
    val editorAttributePath = AttributePath.parse("editor")

    val summaryAttributePath = AttributePath.parse("summary")


    //-----------------------------------------------------------------------------------------------------------------
    // The wrapper named under [metadataKey], or null when the attribute declares none - callers decide what that
    // means (the editor manager falls back to DefaultAttributeEditor, the view manager renders nothing).
    // A blank value counts as absent: it can only come from hand-edited notation, and an ObjectName("") would
    // resolve to no wrapper at all.
    fun wrapperName(
        attributeMetadata: AttributeMetadata,
        metadataKey: AttributePath
    ): ObjectName? {
        return attributeMetadata
            .attributeMetadataNotation
            .get(metadataKey.toNesting())
            ?.asString()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ObjectName(it) }
    }


    fun wrapperName(
        graphStructure: GraphStructure,
        objectLocation: ObjectLocation,
        attributeName: AttributeName,
        metadataKey: AttributePath
    ): ObjectName? {
        val attributeMetadata = graphStructure
            .graphMetadata
            .get(objectLocation)
            ?.attributes
            ?.get(attributeName)
            ?: return null

        return wrapperName(attributeMetadata, metadataKey)
    }
}
