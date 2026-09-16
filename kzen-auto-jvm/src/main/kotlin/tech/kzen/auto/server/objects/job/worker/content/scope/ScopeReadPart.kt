package tech.kzen.auto.server.objects.job.worker.content.scope

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.format.ConfiguredRecordFormat
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.read.DataContentFingerprint
import tech.kzen.auto.common.paradigm.job.control.JobControl
import tech.kzen.auto.server.data.DataOpenerLookup
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.auto.server.objects.job.worker.content.Entry
import tech.kzen.auto.server.objects.job.worker.data.DataReadCore
import tech.kzen.lib.common.exec.MapExecutionValue
import tech.kzen.lib.common.exec.TextExecutionValue
import tech.kzen.lib.common.exec.data.value.DataValue
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service


/**
 * `ReadPart` INSIDE an entry scope (content streaming spike CS2, docs/plans/2026-09-16_borrowed-elements.md
 * §4): opens each [Entry]'s borrowed content through the SAME reader chain `ReadPart` uses over a file —
 * [format]'s resolved read spec (its dialect, header mode and declared schema), the content-coding wrap, the
 * run read policy, the reader capability — entered below the content provider via
 * [tech.kzen.auto.server.data.ContentDataOpener.openContent], and emits the rows as the lifted literal records
 * [DataReadCore] produces, one at a time, downstream in the body chain. A row carries no owner (the reader
 * converts each record into a value of its own), so rows may leave the scope; the entry's bytes never do.
 *
 * The item shape is established from the first entry and every later entry must match it (the `strict` rule of
 * [DataReadCore.establishShape]); a format with a declared schema fixes it up front. The cursor is closed at
 * the end of each entry, and closed WITHOUT draining when the body downstream completes early
 * ([tech.kzen.auto.server.objects.job.channel.DownstreamClosedException] passes through untouched).
 */
@Reflect
class ScopeReadPart(
    private val format: ConfiguredRecordFormat,
    @Service private val openerLookup: DataOpenerLookup
):
    ScopeBodyWorker
{
    companion object {
        private const val fingerprintIdentity = "tech.kzen.auto/archive-entry-v1"
    }


    private var shapeBaseline: DataReadCore.ShapeBaseline? = null
    private var rows = 0L

    /** Carried across a live edit (spike CS3): the shape every later entry must match, and the row count. */
    private class Carried(
        val shapeBaseline: DataReadCore.ShapeBaseline?,
        val rows: Long
    )


    //-----------------------------------------------------------------------------------------------------------------
    override fun captureState(): Any =
        Carried(shapeBaseline, rows)


    override fun loadState(state: Any?) {
        val carried = state as Carried
        shapeBaseline = carried.shapeBaseline
        rows = carried.rows
    }


    //-----------------------------------------------------------------------------------------------------------------
    override suspend fun onElement(element: DataValue, emit: BodyEmitter, control: JobControl) {
        val native = JobDataValues.native(element)
        val entry = native as? Entry
            ?: throw IllegalStateException(
                "ReadPart in a scope requires an Entry, but received " +
                        (native?.let { "${it::class.qualifiedName}: $it" } ?: "null"))

        val part = part(entry)
        val bytes = control.runBlockingIo { entry.content.open() }
        // openContent owns the bytes from here: it closes them itself when the reader fails to open
        val cursor: DataCursor = openerLookup.contentOpener().openContent(part, bytes)

        var completed = false
        try {
            val origin = "entry '${entry.name}' of '${entry.parent.name}'"
            shapeBaseline = DataReadCore.establishShape(
                shapeBaseline,
                DataReadCore.effectiveShape(cursor.shape, null, origin))

            while (true) {
                val emitted = DataReadCore.emitNext(
                    control,
                    cursor,
                    requireNotNull(shapeBaseline),
                    null,
                    claimBeforeSend = { rows += 1 },
                    send = emit::send)
                if (!emitted) {
                    break
                }
            }
            completed = true
        }
        finally {
            if (completed) {
                DataReadCore.close(control, cursor)
            }
            else {
                // Failure or an early downstream close: released without draining the rest of the entry
                DataReadCore.closeFallback(cursor)
            }
        }
    }


    private fun part(entry: Entry): DataPart {
        val ref = DataRef(null, "${entry.parent.name}!${entry.name}")
        val fingerprint = DataContentFingerprint(
            fingerprintIdentity,
            MapExecutionValue(linkedMapOf(
                "archive" to TextExecutionValue(entry.parent.name),
                "entry" to TextExecutionValue(entry.name),
                "size" to TextExecutionValue(entry.size.toString()),
                "modified" to TextExecutionValue(entry.modifiedEpochMillis?.toString() ?: ""))))
        return DataPart(DataRole.main, ref, fingerprint, format.resolvedRead(ref))
    }
}
