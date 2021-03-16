package tech.kzen.auto.server.objects.report.pipeline.input

import org.junit.Test
import tech.kzen.auto.server.objects.report.pipeline.input.parse.tsv.pipeline.TsvProcessorDefiner
import tech.kzen.auto.server.objects.report.pipeline.input.v2.ProcessorHeaderReader
import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.DataLocationInfo
import tech.kzen.auto.server.objects.report.pipeline.input.v2.model.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.report.pipeline.input.v2.read.FlatDataSource
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

        val headerReader = ProcessorHeaderReader()

        val header = headerReader.extract(
            FlatDataHeaderDefinition(
                DataLocationInfo.literalUtf8,
                FlatDataSource.ofLiteral(contents.toByteArray()),
                processorDataDefinition))

        assertEquals(listOf("foo", "bar"), header.values)
    }
}