package tech.kzen.auto.server.data

import tech.kzen.auto.common.data.schema.HeaderListing
import tech.kzen.auto.plugin.api.managed.TraversableReportOutput
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataHeaderDefinition
import java.util.function.Consumer


class ReportHeaderReader(
    private val dataBlockSize: Int = DataBlockBuffer.defaultBytesSize
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun <T> extract(
        flatDataHeaderDefinition: FlatDataHeaderDefinition<T>
    ): HeaderListing {
        var chain: ReportInputChain<T>? = null

        val traversable = object: TraversableReportOutput<T> {
            override fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
                if (chain == null) {
                    chain = flatDataHeaderDefinition.openInputChain(dataBlockSize)
                }
                return chain.poll(visitor)
            }
        }

        return try {
            flatDataHeaderDefinition.extract(traversable)
        }
        finally {
            chain?.close()
        }
    }
}
