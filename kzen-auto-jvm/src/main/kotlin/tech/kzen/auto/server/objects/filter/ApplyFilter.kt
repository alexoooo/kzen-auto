package tech.kzen.auto.server.objects.filter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.filter.CriteriaSpec
import tech.kzen.auto.common.paradigm.common.model.ExecutionResult
import tech.kzen.auto.common.paradigm.common.model.ExecutionSuccess
import tech.kzen.auto.common.paradigm.common.model.ExecutionValue
import tech.kzen.auto.server.objects.filter.model.RecordStream
import java.io.BufferedWriter
import java.nio.file.Files
import java.nio.file.Path


object ApplyFilter {
    private val logger = LoggerFactory.getLogger(ApplyFilter::class.java)


    suspend fun applyFilter(
        inputPaths: List<Path>,
        columnNames: List<String>,
        outputPath: Path,
        criteriaSpec: CriteriaSpec
    ): ExecutionResult {
        if (! Files.isDirectory(outputPath.parent)) {
            withContext(Dispatchers.IO) {
                Files.createDirectories(outputPath.parent)
            }
        }

        logger.info("Starting: $outputPath | $criteriaSpec | $inputPaths")

        withContext(Dispatchers.IO) {
            Files.newBufferedWriter(outputPath).use { output ->
                var first = true
                for (inputPath in inputPaths) {
                    logger.info("Reading: $inputPath")

                    FileStreamer.open(inputPath)!!.use { stream ->
                        filterStream(stream, output, columnNames, criteriaSpec, first)
                    }

                    first = false
                }
            }
        }

        logger.info("Done")

        return ExecutionSuccess.ofValue(ExecutionValue.of(outputPath.toString()))
    }


    private fun filterStream(
        input: RecordStream,
        output: BufferedWriter,
        columnNames: List<String>,
        criteriaSpec: CriteriaSpec,
        writHeader: Boolean
    ) {
        val csvPrinter = CSVFormat.DEFAULT.print(output)

        if (writHeader) {
            csvPrinter.printRecord(columnNames)
        }

        var count = 0
        var writtenCount = 0

        next_record@
        while (input.hasNext()) {
            val record = input.next()

            count++
            if (count % 10_000 == 0) {
                logger.info("Processed $count, wrote $writtenCount")
            }

            for (e in criteriaSpec.columnRequiredValues) {
                val value = record.get(e.key)
                if (! e.value.contains(value)) {
                    continue@next_record
                }
            }

            writtenCount++

            val values = record.getAll(columnNames)
            csvPrinter.printRecord(values)
        }
    }
}