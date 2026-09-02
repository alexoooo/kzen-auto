package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.api.DataOpener
import tech.kzen.auto.common.data.model.DataPart
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity


interface OperationalDataOpener: DataOpener {
    fun adoptionIdentity(part: DataPart): CursorAdoptionIdentity
}
