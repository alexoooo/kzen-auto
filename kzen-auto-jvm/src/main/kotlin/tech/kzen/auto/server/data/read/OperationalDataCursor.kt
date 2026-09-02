package tech.kzen.auto.server.data.read

import tech.kzen.auto.common.data.api.DataCursor
import tech.kzen.auto.common.data.read.CursorAdoptionIdentity


interface OperationalDataCursor: DataCursor {
    val adoptionIdentity: CursorAdoptionIdentity
}
