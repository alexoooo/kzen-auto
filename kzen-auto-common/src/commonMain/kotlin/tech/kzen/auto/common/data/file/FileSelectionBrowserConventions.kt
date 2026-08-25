package tech.kzen.auto.common.data.file

import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation


/**
 * Locates the browser navigation state used by a file-selection attribute editor.
 *
 * The state is deliberately separate from a file source's runtime `directory` and `filter`: neither browsing nor
 * removing the last explicit selection may turn the directory last visited by the chooser into a runtime directory
 * query. A `browser: <attribute path>` scalar in the selection attribute's metadata opts into the separate state;
 * `FileDataSourceConfig` declares it once, so every file source and source Worker inherits it.
 *
 * An absent marker means the editor keeps navigation in component state and persists only the selection — the safe
 * default for the legacy `MultiFileReaderWorker.paths` attribute and for any third-party attribute that has not
 * opted in.
 */
object FileSelectionBrowserConventions {
    val metadataKey = AttributeSegment.ofKey("browser")

    const val defaultDirectory = "./"
    const val defaultFilter = ""

    private val directorySegment = AttributeSegment.ofKey("directory")
    private val filterSegment = AttributeSegment.ofKey("filter")


    fun browserAttributePath(attributeMetadata: MapAttributeNotation): AttributePath? {
        val path = attributeMetadata[metadataKey]?.asString()?.takeIf { it.isNotBlank() }
            ?: return null
        return AttributePath.parse(path)
    }


    fun directoryAttributePath(attributeMetadata: MapAttributeNotation): AttributePath? {
        return browserAttributePath(attributeMetadata)?.nest(directorySegment)
    }


    fun filterAttributePath(attributeMetadata: MapAttributeNotation): AttributePath? {
        return browserAttributePath(attributeMetadata)?.nest(filterSegment)
    }
}
