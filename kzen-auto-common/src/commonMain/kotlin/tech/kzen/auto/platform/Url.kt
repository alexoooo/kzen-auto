package tech.kzen.auto.platform

import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.common.util.digest.Digestible


// A url value object whose identity IS the location string it was given, verbatim.
//
// Deliberately NOT backed by a platform url parser. Until 2026-07-17 this was an `expect class` over
// java.net.URI (jvm) and org.w3c.dom.url.URL (js), and those two disagree: WHATWG normalizes unconditionally on
// construction and cannot be told not to, while RFC 2396 normalizes nothing and outright rejects a space the
// other %-encodes. So the same string yielded a different canonical form — and therefore a different digest() —
// on client and server. Reconciling them cost a ~200-line shared canonicalizer plus a both-platform contract
// test to keep the two reconciled, and it bought nothing: production reads only `parse` (DataLocation's null
// gate) and `path.isEmpty()` (DataLocation.parent), while `scheme` and `query` have no production callers at
// all. Both parsers are therefore gone. ONE implementation means there is no divergence to reconcile and no
// normalization to disagree about — exactly like the sibling value object FilePath, which DataLocation holds
// alongside this one, and which never had the problem because there has only ever been one of it.
//
// Do not reintroduce a platform parser here. If a url ever genuinely needs parsing (real components, IDNA,
// %-encoding), take a multiplatform library so there is still only one implementation — never an expect/actual
// over two parsers, which is what created the divergence in the first place.
//
// Consequences worth knowing:
//   * No normalization. `http://X.com` and `http://x.com` are distinct values, as are `http://a` and
//     `http://a/`. That matches FilePath, and matches what the jvm — where DataLocations are actually
//     produced — did before the canonicalizer briefly existed. Explicit, opt-in normalization lives in
//     jvmMain's UrlJvm.normalize(), mirroring FilePathJvm.normalize().
//   * equals/hashCode/digest/toString all key on `location`, so `a == b` <=> `a.digest() == b.digest()` holds
//     BY CONSTRUCTION. The old expect class had to state that as a contract for each actual to honour, and
//     both actuals had violated it, in different directions.
//   * Validation is a scheme check, not a parse: a malformed url is rejected only if its scheme is malformed,
//     otherwise it fails when something tries to open it. The only gate consumer, DataLocation.parse, tries
//     FilePath.parse first, so this only ever sees what FilePath already refused.
class Url private constructor(
    val location: String
): Digestible {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun of(location: String): Url {
            return parse(location)
                ?: throw IllegalArgumentException("Invalid: $location")
        }


        fun parse(location: String): Url? {
            if (! hasValidScheme(location)) {
                return null
            }

            return Url(location)
        }


        // RFC 3986 §3.1: scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ), terminated by ':'.
        // NB: a ONE-character scheme is rejected, though RFC 3986 allows it. In this codebase a leading
        // "<letter>:" is overwhelmingly a Windows drive ("C:\Users\ao"), not a url, and calling that a Url would
        // be a worse answer than rejecting the vanishingly rare single-letter scheme. DataLocation.parse tries
        // FilePath.parse first so a drive never reaches here anyway, but Url.parse is public and should not lie.
        private fun hasValidScheme(location: String): Boolean {
            val schemeEnd = location.indexOf(':')
            if (schemeEnd < 2) {
                return false
            }

            val first = location[0]
            if (first !in 'a'..'z' && first !in 'A'..'Z') {
                return false
            }

            return (1 until schemeEnd).all {
                val character = location[it]
                character in 'a'..'z' ||
                        character in 'A'..'Z' ||
                        character in '0'..'9' ||
                        character == '+' ||
                        character == '-' ||
                        character == '.'
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    // A scheme is case-insensitive (RFC 3986 §3.1), so it is lowercased here — `location`, the identity, keeps
    // whatever case it was given.
    val scheme: String
        get() = location.substringBefore(':').lowercase()


    // Everything after "scheme:", less the authority when hierarchical ("//host/…"), less any query/fragment.
    // The expectations this has to meet are the ones UrlTest already pinned against java.net.URI's
    // `path ?: schemeSpecificPart.substringBefore("?")` — in particular an opaque path is returned whole
    // (jdbc:h2:file:./work/foo) and the network-file form keeps both leading slashes (//server/folder/data.xml).
    val path: String
        get() {
            val afterScheme = location
                .substringAfter(':')
                .substringBefore('?')
                .substringBefore('#')

            if (! afterScheme.startsWith("//")) {
                // an opaque path is not an authority + path, and is returned untouched
                return afterScheme
            }

            val afterAuthoritySlashes = afterScheme.substring(2)
            val pathStart = afterAuthoritySlashes.indexOf('/')

            return when (pathStart) {
                -1 -> ""
                else -> afterAuthoritySlashes.substring(pathStart)
            }
        }


    val query: String?
        get() = location
            .substringAfter('?', "")
            .substringBefore('#')
            .ifEmpty { null }


    //-----------------------------------------------------------------------------------------------------------------
    override fun digest(sink: Digest.Sink) {
        sink.addUtf8(location)
    }


    override fun toString(): String {
        return location
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Url

        return location == other.location
    }


    override fun hashCode(): Int {
        return location.hashCode()
    }
}
