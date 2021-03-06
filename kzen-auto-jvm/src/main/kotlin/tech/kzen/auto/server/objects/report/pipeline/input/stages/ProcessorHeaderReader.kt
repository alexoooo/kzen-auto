package tech.kzen.auto.server.objects.report.pipeline.input.stages

import tech.kzen.auto.common.objects.document.report.listing.HeaderListing
import tech.kzen.auto.plugin.api.managed.TraversableProcessorOutput
import tech.kzen.auto.plugin.model.data.DataBlockBuffer
import tech.kzen.auto.plugin.model.ModelOutputEvent
import tech.kzen.auto.server.objects.report.pipeline.input.ProcessorInputChain
import tech.kzen.auto.server.objects.report.pipeline.input.model.data.FlatDataHeaderDefinition
import java.util.function.Consumer


class ProcessorHeaderReader(
    private val dataBlockSize: Int = DataBlockBuffer.defaultBytesSize
) {
    //-----------------------------------------------------------------------------------------------------------------
    fun <T> extract(
        flatDataHeaderDefinition: FlatDataHeaderDefinition<T>
    ): HeaderListing {
        var chain: ProcessorInputChain<T>? = null

        val traversable = object : TraversableProcessorOutput<T> {
            override fun poll(visitor: Consumer<ModelOutputEvent<T>>): Boolean {
                if (chain == null) {
                    chain = flatDataHeaderDefinition.openInputChain(dataBlockSize)
                }
                return chain!!.poll(visitor)
            }
        }

        val headerListing = flatDataHeaderDefinition.extract(traversable)

        chain?.close()

        return headerListing
    }
}