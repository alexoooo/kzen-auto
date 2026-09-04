package tech.kzen.auto.client.objects.document.common.file

import js.objects.unsafeJso
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.data.format.ConfiguredFormatDetail
import tech.kzen.auto.common.data.format.FileFormatCatalog


/**
 * Turns the served catalogue into select options.  Persisted values that are no longer offered remain visible,
 * but the maintained configured-format path has no blank/default sentinel: the format reference and its charset
 * are explicit parts of the authored snapshot.
 */
internal object DataFormatOptions {
    fun formats(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        formatOptions(catalog?.formats.orEmpty(), current)


    fun entryFormats(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        withDefault(
            formatOptions(catalog?.formats.orEmpty().filter { it.perFileOverrideAvailable }, current),
            "Use source format")


    private fun formatOptions(
        formats: List<ConfiguredFormatDetail>,
        current: String
    ): Array<SelectOption> {
        val known = formats.map { format ->
            option(
                format.reference,
                format.label,
                format.extensions.takeIf { it.isNotEmpty() }?.joinToString(", ") { ".$it" })
        }
        return withCurrent(known, current)
    }


    fun encodings(catalog: FileFormatCatalog?, current: String): Array<SelectOption> {
        val known = catalog?.encodings.orEmpty().map { option(it, it, null) }
        return withCurrent(known, current)
    }


    fun entryEncodings(catalog: FileFormatCatalog?, current: String): Array<SelectOption> =
        withDefault(encodings(catalog, current), "Detect encoding")


    private fun withDefault(known: Array<SelectOption>, label: String): Array<SelectOption> =
        arrayOf(option("", label, null), *known)


    private fun withCurrent(known: List<SelectOption>, current: String): Array<SelectOption> {
        val options = known.toMutableList()
        if (current.isNotBlank() && known.none { it.value == current }) {
            options.add(option(current, current, "not offered by this server"))
        }
        return options.toTypedArray()
    }


    private fun option(value: String, label: String, detail: String?): SelectOption =
        unsafeJso {
            this.value = value
            this.label = label
            this.detail = detail
        }
}
