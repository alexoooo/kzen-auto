package tech.kzen.auto.server.objects.report.pipeline.input.v2.read

import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.DataLocationInfo


interface FlatDataSource {
    companion object {
        fun ofLiteral(bytes: ByteArray): FlatDataSource {
            return object : FlatDataSource {
                override fun open(dataLocationInfo: DataLocationInfo): FlatDataStream {
                    return InputStreamFlatDataStream.ofLiteral(bytes)
                }
            }
        }
    }


    fun open(
        dataLocationInfo: DataLocationInfo
    ): FlatDataStream
}