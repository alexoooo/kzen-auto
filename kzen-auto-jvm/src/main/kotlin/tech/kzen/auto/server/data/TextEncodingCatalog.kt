package tech.kzen.auto.server.data

import java.nio.charset.Charset


/**
 * The text encodings this JVM can decode a file with, as an ordered option list.
 *
 * Everything installed is offered, because a file is in whatever encoding it is in and a shorter list would
 * simply block reading some of them. The handful people actually reach for is ordered first so the select's
 * unfiltered view is useful; the rest stay one keystroke away through the field's type-ahead.
 */
object TextEncodingCatalog {
    // Ordered by how often a real input file turns out to be in one of them, not alphabetically.
    private val preferred = listOf(
        "UTF-8", "windows-1252", "ISO-8859-1", "US-ASCII", "UTF-16", "UTF-16LE", "UTF-16BE")


    fun available(): List<String> {
        val installed = Charset.availableCharsets().keys
        val leading = preferred.filter { it in installed }
        return leading + installed.filterNot { it in leading }.sorted()
    }
}
