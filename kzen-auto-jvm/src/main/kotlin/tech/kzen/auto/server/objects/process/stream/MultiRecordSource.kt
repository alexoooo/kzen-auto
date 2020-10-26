package tech.kzen.auto.server.objects.process.stream

//import com.google.common.io.MoreFiles
//import org.apache.commons.csv.CSVFormat
//import tech.kzen.auto.common.paradigm.task.api.TaskHandle
//import tech.kzen.auto.server.objects.process.model.RecordItem
//import java.io.BufferedReader
//import java.io.InputStream
//import java.io.InputStreamReader
//import java.nio.file.Files
//import java.nio.file.Path
//import java.nio.file.Paths
//import java.util.zip.GZIPInputStream
//
//
//class MultiRecordSource(
//    private val inputPaths: List<Path>,
//    private val handle: TaskHandle
//):
//    RecordStream
//{
//    //-----------------------------------------------------------------------------------------------------------------
//    companion object {
//        private fun open(inputPath: Path): RecordStream {
//            val extension = MoreFiles.getFileExtension(inputPath)
//            val withoutExtension = MoreFiles.getNameWithoutExtension(inputPath)
//
//            var input: InputStream? = null
//            var reader: BufferedReader? = null
//            try {
//                val adjustedExtension: String
//                val rawInput = Files.newInputStream(inputPath)
//
//                input =
//                    if (extension == "gz") {
//                        adjustedExtension = MoreFiles.getFileExtension(Paths.get(withoutExtension))
//                        GZIPInputStream(rawInput)
//                    }
//                    else {
//                        adjustedExtension = extension
//                        rawInput
//                    }
//
//                reader = BufferedReader(InputStreamReader(input!!))
//
//                return when (adjustedExtension) {
//                    "csv" -> {
//                        CsvRecordStream(CSVFormat
//                            .DEFAULT
//                            .withFirstRecordAsHeader()
//                            .parse(reader))
//                    }
//
//                    "tsv" -> {
//                        TsvRecordStream(reader)
//                    }
//
//                    else -> {
//                        throw UnsupportedOperationException("Unknown file type: $inputPath")
//                    }
//                }
//            }
//            catch (e: Exception) {
//                reader?.close()
//                input?.close()
//                throw IllegalArgumentException(e)
//            }
//        }
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    private var current: RecordStream
//    private var nextIndex = 0
//
//    init {
//        current = open(inputPaths[nextIndex])
//        nextIndex++
//    }
//
//
//    //-----------------------------------------------------------------------------------------------------------------
//    override fun header(): List<String> {
//        return current.header()
//    }
//
//
//    override fun hasNext(): Boolean {
//        if (current.hasNext()) {
//            return true
//        }
//
//        current.close()
//
//        while (nextIndex < inputPaths.size) {
//            current = open(inputPaths[nextIndex])
//            nextIndex++
//
//            if (current.hasNext()) {
//                return true
//            }
//
//            current.close()
//        }
//
//        return false
//    }
//
//
//    override fun next(): RecordItem {
//        return current.next()
//    }
//
//
//    override fun close() {
//        current.close()
//    }
//
//
//
////    //-----------------------------------------------------------------------------------------------------------------
////    override fun streamCount(): Int {
////        return inputPaths.size
////    }
////
////
////    override fun openStream(streamIndex: Int): RecordStream {
////        return open(inputPaths[streamIndex])
////    }
////
////
////    override fun streamName(streamIndex: Int): String {
////        return inputPaths[streamIndex].fileName.toString()
////    }
//}