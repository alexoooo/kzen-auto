package tech.kzen.auto.server.objects.job.worker.format

import tech.kzen.auto.server.objects.job.worker.content.FileNameTemplate
import tech.kzen.lib.common.exec.data.type.DataContract
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.exec.data.value.ValueMetadata


/**
 * Format's `name` [template] over values of [contract], resolved per value into one reused builder: `${extension}`
 * is the format's [extension], a placeholder naming a payload column reads that column (through [cells]), and any
 * other is a dotted path into the value's metadata, as Write reads it ([FileNameTemplate]). A placeholder that is
 * none of these fails by name when the name is made, before Run where the contract is known.
 *
 * The metadata's texts are read once per metadata object, which a reader shares across a part's values, so a name
 * of constants and metadata costs nothing per value, and a name over a column only appends its characters.
 */
internal class FormatName(
    private val template: String,
    private val extension: String,
    private val cells: FormatCells,
    contract: DataContract
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        const val extensionPlaceholder = "extension"
    }


    private sealed interface Part {
        class Literal(val text: String): Part
        data object Extension: Part
        class Column(val column: Int): Part
        class Metadata(val path: String, val slot: Int): Part
    }


    //-----------------------------------------------------------------------------------------------------------------
    private val parts: Array<Part>
    private val metadataTexts: Array<String?>
    private var textsRead = false
    private var textsFor: ValueMetadata? = null
    private val builder = StringBuilder()


    init {
        val parsed = ArrayList<Part>()
        val metadataContract = contract.metadata?.contract
        var metadataCount = 0
        var literalStart = 0
        for (match in FileNameTemplate.placeholder.findAll(template)) {
            if (match.range.first > literalStart) {
                parsed += Part.Literal(template.substring(literalStart, match.range.first))
            }
            literalStart = match.range.last + 1

            val name = match.groupValues[1]
            val column = cells.payloadColumn(name)
            parsed += when {
                name == extensionPlaceholder -> Part.Extension
                column >= 0 -> Part.Column(column)
                FileNameTemplate.metadataHas(metadataContract, name) -> Part.Metadata(name, metadataCount++)
                else -> throw IllegalArgumentException(
                    "Name '$template': '\${$name}' is neither a column nor a metadata field")
            }
        }
        if (literalStart < template.length) {
            parsed += Part.Literal(template.substring(literalStart))
        }
        parts = parsed.toTypedArray()
        metadataTexts = arrayOfNulls(metadataCount)
    }


    //-----------------------------------------------------------------------------------------------------------------
    /** The name of [element], whose cells are bound; a reused builder, valid until the next call. */
    fun resolve(element: DataValue): CharSequence {
        val metadata = element.metadata
        if (!textsRead || metadata !== textsFor) {
            readMetadata(metadata)
            textsFor = metadata
            textsRead = true
        }

        builder.setLength(0)
        for (index in parts.indices) {
            when (val part = parts[index]) {
                is Part.Literal -> builder.append(part.text)
                Part.Extension -> builder.append(extension)
                is Part.Column -> if (!cells.payloadIsNull(part.column)) {
                    builder.append(cells.payloadText(part.column))
                }
                is Part.Metadata -> builder.append(metadataTexts[part.slot])
            }
        }
        return builder
    }


    private fun readMetadata(metadata: ValueMetadata?) {
        for (part in parts) {
            if (part is Part.Metadata) {
                metadataTexts[part.slot] = FileNameTemplate.metadataText(metadata?.value, part.path)
                    ?: throw IllegalArgumentException(
                        "Name '$template': the value's metadata has no '${part.path}'")
            }
        }
    }
}
