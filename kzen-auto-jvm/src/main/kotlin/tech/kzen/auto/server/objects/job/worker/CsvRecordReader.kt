package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.plugin.model.record.FlatFileRecord
import tech.kzen.auto.server.objects.report.exec.input.parse.csv.CsvFormatUtils
import java.io.Reader


/**
 * Streaming RFC-4180 CSV record reader for the Job [CsvReaderWorker] — quoted fields, embedded delimiters
 * and embedded newlines, doubled-quote escapes, and either `\n` or `\r\n` line endings.
 *
 * This reuses the Report CSV state machine's STATE CONSTANTS and transition shape (the [CsvFormatUtils]
 * states + the `nextState` switch, templated below as [transition]), but NOT the Report parse pipeline
 * itself: that pipeline is wired into an LMAX-Disruptor that runs its own threads, which would spawn
 * uncounted threads inside a Worker coroutine and break the Job's [tech.kzen.auto.server.objects.job.CountingDispatcher]
 * quiescence detection. Instead this is a plain pull reader the Worker drives inside `control.runBlockingIo`,
 * one [readRecord] per blocking unit. [CsvFormatUtils.nextState] also hard-codes the comma delimiter, so the
 * transition here is parameterized by [delimiterChar] (the `;`-delimited 1BRC file is the canary).
 *
 * Records are built into a freshly-allocated [FlatFileRecord] via the safe `addToField` / `commitField`
 * API. The `FlatFileRecord()` allocation in [readRecord] is the single allocation seam a future record pool
 * would slot into (see [DataRecord] — memory-reuse is a documented design seam, not yet implemented).
 */
class CsvRecordReader(
    private val reader: Reader,
    delimiter: String
):
    AutoCloseable
{
    //-----------------------------------------------------------------------------------------------------------------
    private val delimiterChar: Char =
        if (delimiter.isEmpty()) CsvFormatUtils.delimiter else delimiter[0]

    private val buffer = CharArray(8192)
    private var bufferLength = 0
    private var bufferPosition = 0
    private var endOfStream = false

    // One-char push-back, used to swallow the `\n` of a `\r\n` pair without mis-reading it as an empty record.
    private var pushedBack = -1


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Reads and returns the next record, or null at end of input. A record ends at an unquoted line break
     * (or at end of input); a line break inside a quoted field is content, not a terminator.
     */
    fun readRecord(): FlatFileRecord? {
        var state = CsvFormatUtils.stateStartOfField
        val record = FlatFileRecord()
        var sawAnyChar = false

        while (true) {
            val next = read1()
            if (next < 0) {
                break
            }
            val nextChar = next.toChar()
            sawAnyChar = true

            val previousState = state
            state = transition(previousState, nextChar)

            when (state) {
                CsvFormatUtils.stateStartOfField ->
                    // Transitioned into start-of-field => a delimiter ended the field just built.
                    record.commitField()

                CsvFormatUtils.stateInUnquoted ->
                    record.addToField(nextChar)

                CsvFormatUtils.stateInQuoted ->
                    // Inside quotes: a quote char is only content when it's the second of a doubled pair.
                    if (nextChar != CsvFormatUtils.quotation || previousState == CsvFormatUtils.stateInQuotedQuote) {
                        record.addToField(nextChar)
                    }

                CsvFormatUtils.stateInQuotedQuote ->
                    // Saw a quote inside a quoted field; whether it's an escape or the close is decided next char.
                    Unit

                CsvFormatUtils.stateEndOfRecord -> {
                    record.commitField()
                    if (nextChar == CsvFormatUtils.carriageReturn) {
                        // Swallow a following '\n' (CRLF); push anything else back for the next record.
                        val following = read1()
                        if (following >= 0 && following.toChar() != CsvFormatUtils.lineFeed) {
                            pushedBack = following
                        }
                    }
                    return record
                }
            }
        }

        if (! sawAnyChar) {
            // End of input with nothing pending: no more records.
            return null
        }

        // End of input without a trailing line break: commit the final field of the last record.
        record.commitField()
        return record
    }


    //-----------------------------------------------------------------------------------------------------------------
    // CsvFormatUtils.nextState, with the comma delimiter generalized to delimiterChar.
    private fun transition(currentState: Int, nextChar: Char): Int {
        return when (currentState) {
            CsvFormatUtils.stateStartOfField, CsvFormatUtils.stateEndOfRecord ->
                when (nextChar) {
                    CsvFormatUtils.quotation -> CsvFormatUtils.stateInQuoted
                    delimiterChar -> CsvFormatUtils.stateStartOfField
                    CsvFormatUtils.carriageReturn, CsvFormatUtils.lineFeed -> CsvFormatUtils.stateEndOfRecord
                    else -> CsvFormatUtils.stateInUnquoted
                }

            CsvFormatUtils.stateInUnquoted ->
                when (nextChar) {
                    delimiterChar -> CsvFormatUtils.stateStartOfField
                    CsvFormatUtils.carriageReturn, CsvFormatUtils.lineFeed -> CsvFormatUtils.stateEndOfRecord
                    else -> CsvFormatUtils.stateInUnquoted
                }

            CsvFormatUtils.stateInQuoted ->
                if (nextChar == CsvFormatUtils.quotation)
                    CsvFormatUtils.stateInQuotedQuote
                else
                    CsvFormatUtils.stateInQuoted

            CsvFormatUtils.stateInQuotedQuote ->
                when (nextChar) {
                    CsvFormatUtils.quotation -> CsvFormatUtils.stateInQuoted
                    delimiterChar -> CsvFormatUtils.stateStartOfField
                    CsvFormatUtils.carriageReturn, CsvFormatUtils.lineFeed -> CsvFormatUtils.stateEndOfRecord
                    else -> CsvFormatUtils.stateInUnquoted
                }

            else ->
                throw IllegalStateException("Unknown state: $currentState")
        }
    }


    private fun read1(): Int {
        if (pushedBack >= 0) {
            val popped = pushedBack
            pushedBack = -1
            return popped
        }

        if (bufferPosition >= bufferLength) {
            if (endOfStream) {
                return -1
            }
            bufferLength = reader.read(buffer)
            bufferPosition = 0
            if (bufferLength <= 0) {
                endOfStream = true
                return -1
            }
        }

        return buffer[bufferPosition++].code
    }


    override fun close() {
        reader.close()
    }
}
