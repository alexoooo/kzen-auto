package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


/**
 * The self-describing element of the records data lane: one [record] plus a SHARED reference to the [header]
 * that names its columns.
 *
 * Replaces the old `RecordBatch` hack. Batching is now a general Channel-framework concern (elements are
 * transparently batched for cross-Worker transfer — see [tech.kzen.auto.server.objects.job.channel.JobChannel]),
 * so the DOMAIN element is a single record rather than a batch, and the framework stays agnostic of records /
 * schemas (it just carries `Any?`). The [header] reference is SHARED across every record of a given schema
 * (cheap — one pointer, not a copy), which is what keeps a self-describing element affordable: a Worker that
 * changes the schema (e.g. [FormulaWorker] appending a column) emits records carrying a NEW shared header, so
 * a schema change is simply the header reference the downstream records point at changing. Non-record lanes
 * (the scalar / Run lane) carry their own element type with no header at all.
 *
 * Each record is freshly allocated and ownership-transferred through the channel, so a sender never mutates it
 * after emitting — race-free without copying. A Worker that RECEIVED a record MAY mutate it in place before
 * forwarding (e.g. [FormulaWorker] appends calculated fields), since ownership has transferred to it.
 */
class DataRecord(
    val header: HeaderListing,
    val record: FlatFileRecord
)
