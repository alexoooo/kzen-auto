package tech.kzen.auto.platform

import java.net.URI


object UrlJvm {
    // Explicit, opt-in, and jvm-only — mirroring FilePathJvm.normalize(), its counterpart on the other half of
    // a DataLocation. Url.of() itself never normalizes, and that is precisely what keeps client and server
    // agreeing on a url's identity (see Url's banner). java.net.URI is fine HERE for the same reason: this is
    // off the identity path and never runs on js, so it has nothing to diverge from.
    fun Url.normalize(): Url {
        val normalized =
            try {
                URI(location).normalize().toString()
            }
            catch (e: Exception) {
                // java.net.URI is stricter than Url.parse — it rejects a literal space, among others. Having
                // nothing to normalize is not an error: the value simply stays as given.
                return this
            }

        return Url.parse(normalized)
            ?: this
    }
}
