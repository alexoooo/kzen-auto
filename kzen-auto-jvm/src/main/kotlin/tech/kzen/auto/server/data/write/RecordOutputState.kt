package tech.kzen.auto.server.data.write


/**
 * What a [RecordEncoder] remembers about one output between its header and its footer (e.g. that no record has been
 * written yet, for a format that separates records), made by [RecordEncoder.openOutput] and passed back on every
 * call for that output. Opaque to the caller, which keeps one per open output.
 */
interface RecordOutputState {
    /** The state of every output of a format that remembers nothing about an output. */
    object Stateless: RecordOutputState
}
