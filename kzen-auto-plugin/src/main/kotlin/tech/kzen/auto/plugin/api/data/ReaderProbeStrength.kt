package tech.kzen.auto.plugin.api.data


/**
 * How much a probe's match claims, weakest first; detection keeps the strongest tier and treats a tie within it
 * as ambiguity (the input is then preserved as text).
 */
enum class ReaderProbeStrength {
    /** The bounded sample is consistent with the format (a structural guess: rows of equal width, say). */
    ContentStrong,

    /**
     * The sample carries the format's own signature — magic bytes, a fixed header row, a decoded framing — so it
     * outranks a structural guess from a generic reader that would accept any regular text.
     */
    ContentSignature,

    /** The filename named the format's family and the content validated under it. */
    ExtensionValidated
}
