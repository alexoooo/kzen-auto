package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity


interface OperationalDataCursor: DataCursor {
    val adoptionIdentity: CursorAdoptionIdentity

    /**
     * The bytes of the stored content read so far, beneath any coding (compressed bytes for a gzip file), so a
     * reader's progress compares with the content's size; null when this cursor does not read bytes it can count.
     * Travels with the cursor across a detach / adopt, so it stays the real position after a migration.
     */
    val sourceBytesRead: Long?
        get() = null
}
