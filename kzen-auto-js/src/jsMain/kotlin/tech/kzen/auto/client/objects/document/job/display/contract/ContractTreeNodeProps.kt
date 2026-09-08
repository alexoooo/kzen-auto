package tech.kzen.auto.client.objects.document.job.display.contract

import react.Props
import tech.kzen.lib.common.exec.data.type.DataContract

external interface ContractTreeNodeProps: Props {
    var contract: DataContract
    var label: String
    var optional: Boolean
}
