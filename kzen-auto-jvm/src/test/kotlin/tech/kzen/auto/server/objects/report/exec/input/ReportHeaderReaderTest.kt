package tech.kzen.auto.server.objects.report.exec.input

import org.junit.Test
import tech.kzen.auto.server.objects.report.exec.input.connect.FlatDataSource
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataHeaderDefinition
import tech.kzen.auto.server.objects.report.exec.input.model.data.FlatDataLocation
import tech.kzen.auto.server.objects.report.exec.input.parse.tsv.TsvReportDefiner
import tech.kzen.auto.server.objects.report.exec.input.stages.ReportHeaderReader
import kotlin.test.assertEquals


class ReportHeaderReaderTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun readFromFile() {
//        val location = DataLocation(Paths.get(
//                "C:/~/data/tsv-world.txt").toUri())
//
//        val encoding = TextEncodingSpec.utf8
//
        val processorDataDefinition = TsvReportDefiner.instance.define()

//        val header = ProcessorHeaderReader.ofFile(
//                processorDataDefinition.data,
//                FirstRecordItemHeaderExtractor(),
//                location,
//                DataEncodingSpec(encoding))

        val contents =
                "foo\tbar\n" +
                "hello\t420"

        val headerReader = ReportHeaderReader()

        val header = headerReader.extract(
            FlatDataHeaderDefinition(
                FlatDataLocation.literalUtf8,
                FlatDataSource.ofLiteral(contents.toByteArray()),
                processorDataDefinition))

        assertEquals(listOf("foo", "bar"), header.values.map { it.text })
    }
}