package tech.kzen.auto.server.objects.job.value

import tech.kzen.lib.common.exec.data.value.DataValue


/** Trace state relevant to whether transport may prove an exclusive value move. */
enum class TraceAliasState {
    None,
    SynchronousSnapshotComplete,
    LiveInspector
}


data class JobTransferConditions(
    val receiverCount: Int = 1,
    val senderRetainsAlias: Boolean = false,
    val trace: TraceAliasState = TraceAliasState.None,
    val replayRetainsValue: Boolean = false,
    val fanOut: Boolean = false
) {
    init {
        require(receiverCount > 0)
    }

    val isExclusive: Boolean
        get() = receiverCount == 1 &&
                !senderRetainsAlias &&
                trace != TraceAliasState.LiveInspector &&
                !replayRetainsValue &&
                !fanOut
}


/** Sender-side handle. Publication invalidates sender use unless retention was explicitly declared. */
internal class JobValueSender(
    private val value: DataValue
) {
    private var published = false
    private var retained = false

    fun current(): DataValue {
        check(!published || retained) { "Sender may not use a value after exclusive publication" }
        return value
    }

    fun publish(conditions: JobTransferConditions): JobValueDelivery {
        check(!published) { "A value may be published from this sender handle only once" }
        published = true
        retained = conditions.senderRetainsAlias
        return JobValueDelivery(value, conditions.isExclusive)
    }
}


/** Physical channel delivery; claim and migration adoption are each at-most-once. */
internal class JobValueDelivery(
    private val value: DataValue,
    private val exclusive: Boolean
) {
    private var consumed = false

    fun claim(): JobValueClaim {
        check(!consumed) { "Job value delivery was already claimed or migrated" }
        consumed = true
        return JobValueClaim(value, exclusive)
    }

    fun migrate(): JobValueDelivery {
        check(!consumed) { "Job value delivery was already claimed or migrated" }
        consumed = true
        return JobValueDelivery(value, exclusive)
    }
}


internal data class JobValueClaim(
    val value: DataValue,
    val exclusive: Boolean
)
