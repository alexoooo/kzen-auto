package tech.kzen.auto.server.data

import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.auto.server.objects.report.exec.input.connect.InputStreamFlatDataStream


/**
 * JVM byte-stream seam consumed by the legacy Report parsing stack. This is not
 * [tech.kzen.auto.common.data.api.DataSource], the notation-discovered query capability that resolves manifests.
 */
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
