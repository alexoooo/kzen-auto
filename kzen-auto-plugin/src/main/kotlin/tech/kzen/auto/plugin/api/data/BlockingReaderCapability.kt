package tech.kzen.auto.plugin.api.data

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.schema.DataShape


/**
 * [ReaderCapability] for authors without coroutines: implement the ordinary blocking [openBlocking] and
 * [inspectBlocking] and the `suspend` pair is provided once here. A Java subclass therefore never sees a
 * `Continuation`. The framework already treats a reader's open, inspect and cursor pulls as blocking I/O and
 * runs them where blocking is accounted for, so the bridge is a direct call rather than a dispatcher hop.
 */
abstract class BlockingReaderCapability: ReaderCapability {
    /** Opens the part described by [request]; the returned cursor's pulls may block. */
    abstract fun openBlocking(request: ReaderOpenRequest): DataCursor

    /** Reads at most [ReaderInspectionRequest.maximumRecords] records to describe the shape. */
    abstract fun inspectBlocking(request: ReaderInspectionRequest): DataShape


    final override suspend fun open(request: ReaderOpenRequest): DataCursor {
        return openBlocking(request)
    }

    final override suspend fun inspect(request: ReaderInspectionRequest): DataShape {
        return inspectBlocking(request)
    }
}
