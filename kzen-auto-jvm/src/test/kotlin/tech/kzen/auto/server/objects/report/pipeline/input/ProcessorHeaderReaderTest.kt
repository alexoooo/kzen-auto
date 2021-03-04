package tech.kzen.auto.server.objects.report.pipeline.input

import org.junit.Test
import tech.kzen.auto.server.objects.report.pipeline.input.parse.common.FirstRecordItemHeaderExtractor
import tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline.TsvProcessorDefiner
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorHeaderReader
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.InputStreamFlatDataReader
import kotlin.test.assertEquals


class ProcessorHeaderReaderTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun readFromFile() {
//        val location = DataLocation(Paths.get(
//                "C:/~/data/tsv-world.txt").toUri())
//
//        val encoding = TextEncodingSpec.utf8
//
        val processorDataDefinition = TsvProcessorDefiner.instance.define()

//        val header = ProcessorHeaderReader.ofFile(
//                processorDataDefinition.data,
//                FirstRecordItemHeaderExtractor(),
//                location,
//                DataEncodingSpec(encoding))

        val contents =
                "foo\tbar\n" +
                "hello\t420"

        val headerReader = ProcessorHeaderReader(
                processorDataDefinition.data,
                FirstRecordItemHeaderExtractor(),
                { InputStreamFlatDataReader.ofLiteral(contents.toByteArray()) },
                Charsets.UTF_8)

        val header = headerReader.extract()

        assertEquals(listOf("foo", "bar"), header)
    }
}