package tech.kzen.auto.client.objects.document.common.file

import js.objects.unsafeJso
import tech.kzen.auto.client.wrap.select.SelectOption
import tech.kzen.auto.common.data.format.FileFormatCatalog


/**
 * Turns the served [FileFormatCatalog] into select options for the two places a file's format and encoding are
 * chosen: a File Worker's defaults under Advanced, and a selected file's overrides under Details.
 *
 * Held in one place so those two cannot disagree about what a blank value means — it is not "unset", it is
 * "decide for me": a Worker's blank format defers to extension inference, and a file's blank defers to its
 * Worker. That is the value both attributes start out holding, so it is a real option rather than an absence.
 *
 * A value the catalogue no longer offers (a plugin removed, a charset absent from this JVM) is still listed, so
 * an existing configuration reads back as what it says instead of as an empty field.
 */
internal object DataFormatOptions {
    const val defaultValue = ""
    private const val defaultLabel = "Default"


    fun formats(catalog: FileFormatCatalog?, current: String): Array<SelectOption> {
        val known = catalog?.formats.orEmpty().map { format ->
            option(
                format.coordinate.asString(),
                format.coordinate.asString(),
                format.extensions.takeIf { it.isNotEmpty() }?.joinToString(", ") { ".$it" })
        }
        return withDefaultAndCurrent(known, current)
    }


    fun encodings(catalog: FileFormatCatalog?, current: String): Array<SelectOption> {
        val known = catalog?.encodings.orEmpty().map { option(it, it, null) }
        return withDefaultAndCurrent(known, current)
    }


    private fun withDefaultAndCurrent(known: List<SelectOption>, current: String): Array<SelectOption> {
        val options = mutableListOf(option(defaultValue, defaultLabel, null))
        options.addAll(known)
        if (current != defaultValue && known.none { it.value == current }) {
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
