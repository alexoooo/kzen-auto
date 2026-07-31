package tech.kzen.auto.common.paradigm.logic

import tech.kzen.lib.common.exec.logic.run.model.LogicRunResponse


/**
 * A run-control response together with the reason the server gives when it refuses it — the `text/plain` body
 * of the move-to (Set Next Statement) endpoint. A reply carrying no reason is the bare [LogicRunResponse] name,
 * which is what every other control verb answers, so one parser reads them all.
 *
 * [reason] is shown to the user as the rejection detail, so it is product copy: which frame could not carry the
 * move and why, in the user's own vocabulary.
 */
data class LogicControlReply(
    val response: LogicRunResponse,
    val reason: String? = null
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val reasonSeparator = ": "


        fun parse(body: String): LogicControlReply {
            val separatorIndex = body.indexOf(reasonSeparator)
            if (separatorIndex == -1) {
                return LogicControlReply(LogicRunResponse.valueOf(body))
            }

            return LogicControlReply(
                LogicRunResponse.valueOf(body.substring(0, separatorIndex)),
                body.substring(separatorIndex + reasonSeparator.length))
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun asString(): String {
        return when (reason) {
            null -> response.name
            else -> response.name + reasonSeparator + reason
        }
    }
}
