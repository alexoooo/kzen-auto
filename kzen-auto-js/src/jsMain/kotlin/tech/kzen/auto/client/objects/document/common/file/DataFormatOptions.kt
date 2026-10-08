package tech.kzen.auto.client.objects.document.common.file

import js.objects.unsafeJso
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FileFormatCatalog


/**
 * Turns the served catalogue into select options.  Persisted values that are no longer offered remain visible,
 * but the maintained configured-format path has no blank/default sentinel: the format reference and its charset
 * are explicit parts of the authored snapshot. Formats defined in the project's own documents are grouped after the
 * built-in ones, each saying which document holds it.
 */
internal object DataFormatOptions {
    private const val builtInGroup = "Built-in"
    private const val projectGroup = "Defined in this project"
    private const val unavailableGroup = "Unavailable"


    fun formats(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        formatOptions(catalog?.formats.orEmpty(), current)


    /** The formats that can write; a current one that cannot is still listed, so the field reads back. */
    fun writableFormats(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        formatOptions(catalog?.formats.orEmpty().filter { it.writable || it.reference == current }, current)


    fun entryFormats(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        withDefault(
            formatOptions(catalog?.formats.orEmpty().filter { it.perFileOverrideAvailable }, current),
            "Use source format")


    // Grouped only once the project defines a format, so a project without any keeps the plain list
    private fun formatOptions(
        formats: List<ConfiguredFormatDetail>,
        current: String
    ): Array<SelectOption> {
        val (project, builtIn) = formats.partition { it.projectDocument != null }
        val grouped = project.isNotEmpty()
        val known = (builtIn + project).map { format ->
            val extensions = format.extensions.takeIf { it.isNotEmpty() }?.joinToString(", ") { ".$it" }
            val document = format.projectDocument
            option(
                format.reference,
                format.label,
                if (document == null) extensions else listOfNotNull("In $document", extensions).joinToString(" · "),
                if (! grouped) null else if (document == null) builtInGroup else projectGroup)
        }
        return withCurrent(known, current, unavailableGroup.takeIf { grouped })
    }


    fun encodings(catalog: FileFormatCatalog?, current: String): Array<SelectOption> {
        val known = catalog?.encodings.orEmpty().map { option(it, it, null) }
        return withCurrent(known, current)
    }


    fun entryEncodings(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        withDefault(encodings(catalog, current), "Detect encoding")


    private fun withDefault(known: Array<SelectOption>, label: String): Array<SelectOption> =
        arrayOf(option("", label, null), *known)


    private fun withCurrent(
        known: List<SelectOption>,
        current: String,
        group: String? = null
    ): Array<SelectOption> {
        val options = known.toMutableList()
        if (current.isNotBlank() && known.none { it.value == current }) {
            options.add(option(current, current, "not offered by this server", group))
        }
        return options.toTypedArray()
    }


    private fun option(value: String, label: String, detail: String?, group: String? = null): SelectOption =
        unsafeJso {
            this.value = value
            this.label = label
            this.detail = detail
            group?.let { this.group = it }
        }
}
