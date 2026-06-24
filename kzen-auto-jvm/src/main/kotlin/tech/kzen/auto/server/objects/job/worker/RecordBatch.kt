package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


/**
 * A batch of records streamed over a Job Channel by the M3 Report-parity slice Workers. Channel elements
 * are **batched** (not one record per `send`) so the per-element coroutine-channel overhead is amortized
 * toward the Report Disruptor's per-element throughput — see kzen/plans/2026-06-23_job-paradigm.md M3.
 *
 * The record type is the plugin [FlatFileRecord] (the performant, general record model — chosen over a
 * `List<String>` so the genuine `CalculatedColumnEval` / pivot engines can be reused; the Job *channels*
 * themselves stay arbitrary-object, this is only what these particular Workers put on them). Each batch is
 * freshly allocated and ownership-transferred through the channel, so its records are never mutated by the
 * sender after `send` — race-free without copying (contrast the Report's single reused mutable
 * `ReportOutputEvent`, which cannot cross a channel). A Worker that received a batch *may* mutate its
 * records in place before forwarding (e.g. [FormulaWorker] appends calculated fields), since ownership has
 * transferred to it.
 *
 * [header] is the shared column listing — the same immutable reference is reused across every batch from a
 * given producer, so carrying it per-batch is a reference, not a copy; [records] are this batch's rows,
 * each positionally aligned to [header].
 */
class RecordBatch(
    val header: HeaderListing,
    val records: List<FlatFileRecord>
)
