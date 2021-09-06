package tech.kzen.auto.server.objects.pipeline.exec.input.connect

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.objects.pipeline.exec.input.model.data.FlatDataLocation


interface FlatDataSource {
    companion object {
        fun ofLiteral(bytes: ByteArray): FlatDataSource {
            return object : FlatDataSource {
                override fun open(flatDataLocation: FlatDataLocation): FlatDataStream {
                    return InputStreamFlatDataStream.ofLiteral(bytes)
                }

                override fun size(dataLocation: DataLocation): Long {
                    return bytes.size.toLong()
                }
            }
        }
    }


    fun open(
        flatDataLocation: FlatDataLocation
    ): FlatDataStream


    /**
     * @return total size in bytes, or -1 if unknown
     */
    fun size(dataLocation: DataLocation): Long
}