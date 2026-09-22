package tech.kzen.auto.common.objects.document.job

import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure


/**
 * What a reading source Worker sends on: the [items] inside each file, or each file whole ([units]). Left on
 * [automatic] the step below decides — a [JobConventions.isFileConsumer] takes files, anything else their
 * contents — so the server's Read lane and the client's column projection must resolve it by this one rule.
 */
object JobReadEmit {
    const val automatic = "auto"
    const val items = "items"
    const val units = "units"


    fun isKnown(declared: String): Boolean =
        declared == automatic || declared == items || declared == units


    fun effective(declared: String, graphStructure: GraphStructure, worker: ObjectLocation): String {
        if (declared != automatic) {
            return declared
        }
        val consumer = JobChannelDerivation.consumerOf(graphStructure, worker)
        return if (consumer != null && JobConventions.isFileConsumer(graphStructure.graphNotation, consumer)) {
            units
        }
        else {
            items
        }
    }
}
