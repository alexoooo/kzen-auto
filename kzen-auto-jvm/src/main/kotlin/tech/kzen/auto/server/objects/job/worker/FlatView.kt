package tech.kzen.auto.server.objects.job.worker

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.model.record.FlatFileRecord


/**
 * The columnar half of a [JobMessage]: the [HeaderListing] naming the columns paired with the one
 * [FlatFileRecord] of values. A record without its schema is meaningless, so the two travel as a single
 * object — a message either has a whole flat part or none, never one half.
 *
 * The [header] reference is SHARED across every view of a given schema (cheap — one pointer per view, not a
 * copy), so a schema change downstream is simply the reference swapping: [FormulaWorker] appends computed
 * fields to [record] in place and assigns the augmented header. Mutation is legal under the message's
 * receiver-ownership contract (see [JobMessage]).
 */
class FlatView(
    var header: HeaderListing,
    val record: FlatFileRecord
)
