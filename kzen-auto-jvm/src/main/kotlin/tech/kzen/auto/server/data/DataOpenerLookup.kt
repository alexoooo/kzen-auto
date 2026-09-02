package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity


class DataOpenerLookup(
    private val plainOpener: DataOpener
) {
    @Suppress("UNUSED_PARAMETER")
    fun openerFor(ref: DataRef): DataOpener = plainOpener


    fun adoptionIdentity(part: DataPart): CursorAdoptionIdentity? =
        (plainOpener as? OperationalDataOpener)?.adoptionIdentity(part)
}
