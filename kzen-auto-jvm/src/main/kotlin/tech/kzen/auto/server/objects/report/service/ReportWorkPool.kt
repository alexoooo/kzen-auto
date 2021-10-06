package tech.kzen.auto.server.objects.report.service

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.common.paradigm.common.v1.model.LogicExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunExecutionId
import tech.kzen.auto.common.paradigm.common.v1.model.LogicRunId
import tech.kzen.auto.server.objects.report.model.ReportRunSignature
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.util.yaml.YamlMap
import tech.kzen.lib.common.util.yaml.YamlNode
import tech.kzen.lib.common.util.yaml.YamlParser
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors


class ReportWorkPool(
    private val workUtils: WorkUtils
) {
    companion object {
        private val logger = LoggerFactory.getLogger(ReportWorkPool::class.java)
        private const val oldDirLimit = 10

        val defaultReportDir = Path.of("report")
        private val reportInfoFile = Path.of("report.yaml")

        private val processSignatureKey = "process-signature"
        private val statusKey = "status"
        private val logicRunIdKey = "run-id"
        private val logicExecutionIdKey = "execution-id"


        fun deleteDir(tempDir: Path) {
            try {
                // NB: .toList throws "Unresolved reference" with Kotlin 1.5.10
                val toDelete = Files
                    .walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList())

                for (path in toDelete) {
                    Files.delete(path)
                }
            }
            catch (e: Exception) {
                logger.error("Unable to cleanup", e)
                throw IOException(e)
            }
        }
    }


    fun resolveRunDir(
        reportRunSignature: ReportRunSignature,
        reportDir: Path
    ): Path {
        val workDir =
            if (reportDir.isAbsolute) {
                reportDir.normalize()
            }
            else {
                workUtils.resolve(reportDir)
            }

        val tempName = WorkUtils.filenameEncodeDigest(reportRunSignature.digest())
        val tempPath = workDir.resolve(tempName)
        return tempPath.toAbsolutePath().normalize()
    }


    fun updateRunStatus(runDir: Path, newStatus: OutputStatus) {
        val infoFile = runDir.resolve(reportInfoFile)
        val infoText = Files.readString(infoFile)
        val infoYaml = YamlParser.parse(infoText) as YamlMap

        @Suppress("UNCHECKED_CAST")
        val infoMap = infoYaml.toObject() as Map<String, String>

        val updatedInfoMap: Map<String, String> =
            infoMap + mapOf(statusKey to newStatus.name)

        val updatedInfoYaml = YamlNode.ofObject(updatedInfoMap)
        val updatedInfoText = YamlParser.unparse(updatedInfoYaml)
        Files.write(infoFile, updatedInfoText.toByteArray())
    }


    fun readRunStatus(runDir: Path): OutputStatus {
//        val dir = resolveRunDir(reportRunSignature)
        val infoFile = runDir.resolve(reportInfoFile)
        if (! Files.exists(infoFile)) {
            return OutputStatus.Corrupt
        }

        val infoText = Files.readString(infoFile)
        val infoYaml = YamlParser.parse(infoText) as YamlMap

        val statusNode = infoYaml.values[statusKey]
            ?: error("Missing: $statusKey")
        val statusText = statusNode.toObject() as String
        val statusValue = OutputStatus.valueOf(statusText)

        if (statusValue != OutputStatus.Running) {
            return statusValue
        }

        val processNode = infoYaml.values[processSignatureKey]
            ?: error("Missing: $processSignatureKey")
        @Suppress("MoveVariableDeclarationIntoWhen")
        val processText = processNode.toObject() as String

        return when (processText) {
            WorkUtils.processSignature ->
                OutputStatus.Running

            else -> OutputStatus.Killed
        }
    }


    fun readRunExecutionId(runDir: Path): LogicRunExecutionId? {
        val infoFile = runDir.resolve(reportInfoFile)
        if (! Files.exists(infoFile)) {
            return null
        }

        val infoText = Files.readString(infoFile)
        val infoYaml = YamlParser.parse(infoText) as YamlMap

        val logicRunIdNode = infoYaml.values[logicRunIdKey]
            ?: error("Missing: $logicRunIdKey")

        val logicExecutionIdNode = infoYaml.values[logicExecutionIdKey]
            ?: error("Missing: $logicExecutionIdKey")

        val logicRunIdText = logicRunIdNode.toObject() as String
        val logicRunId = LogicRunId(logicRunIdText)

        val logicExecutionIdText = logicExecutionIdNode.toObject() as String
        val logicExecutionId = LogicExecutionId(logicExecutionIdText)

        return LogicRunExecutionId(logicRunId, logicExecutionId)
    }


    fun prepareRunDir(
        dir: Path,
        logicRunExecutionId: LogicRunExecutionId
    ) {
        check(! Files.exists(dir)) { "Already exists: $dir" }
        Files.createDirectories(dir)

        val initialInfoYaml = """
            process-signature: "${WorkUtils.processSignature}"
            status: Running
            run-id: "${logicRunExecutionId.logicRunId.value}"
            execution-id: "${logicRunExecutionId.logicExecutionId.value}"
        """.trimIndent()

        Files.write(
            dir.resolve(reportInfoFile),
            initialInfoYaml.toByteArray())
    }
}