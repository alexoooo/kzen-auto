package tech.kzen.auto.common.data.format

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
enum class FormatMaterializationIntent {
    @SerialName("override")
    Override,

    @SerialName("make-explicit")
    MakeExplicit,

    @SerialName("lock-columns")
    LockColumns
}
