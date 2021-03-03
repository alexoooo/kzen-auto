package tech.kzen.auto.server.objects.report.pipeline.input.v2

//import tech.kzen.auto.plugin.api.DataFramer
//import tech.kzen.auto.plugin.model.DataBlockBuffer
//import tech.kzen.auto.server.objects.report.pipeline.input.parse.RecordFormat
//
//
//class PipelineInputFramer {
//    //-----------------------------------------------------------------------------------------------------------------
//    private var previousLocation: String? = null
//    private var previousFramer: DataFramer? = null
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    fun frame(data: DataBlockBuffer) {
//        val framer = nextFramer(data)
//
//        framer.frame(data)
//
//        if (data.endOfStream) {
//            closeLexer(data)
//        }
//    }
//
//
//    private fun nextFramer(data: DataBlockBuffer): DataFramer {
//        if (previousLocation == null) {
//            previousLocation = data.inputKey!!
//            previousFramer = RecordFormat.forExtension(data.innerExtension!!).lexer
//        }
//        return previousFramer!!
//    }
//
//
//    private fun closeLexer(data: DataBlockBuffer) {
//        previousFramer!!.endOfStream(data)
//        previousFramer = null
//        previousLocation = null
//    }
//}