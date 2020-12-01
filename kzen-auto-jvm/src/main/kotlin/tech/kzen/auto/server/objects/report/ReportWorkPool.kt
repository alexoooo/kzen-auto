package tech.kzen.auto.server.objects.report

import org.slf4j.LoggerFactory
import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.server.objects.report.model.ReportRunSignature
import tech.kzen.auto.util.WorkUtils
import tech.kzen.lib.common.util.yaml.YamlMap
import tech.kzen.lib.common.util.yaml.YamlNode
import tech.kzen.lib.common.util.yaml.YamlParser
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path


object ReportWorkPool {
    private val logger = LoggerFactory.getLogger(ReportWorkPool::class.java)

    private const val oldDirLimit = 10
    private val reportDir = Path.of("report")
    private val reportInfoFile = Path.of("report.yaml")

    private val processSignatureKey = "process-signature"
    private val statusKey = "status"


    fun resolveRunDir(reportRunSignature: ReportRunSignature): Path {
        val tempName = reportRunSignature.digest().asString()
        val tempPath = reportDir.resolve(tempName)
        val workDir = WorkUtils.resolve(tempPath)
        return workDir.toAbsolutePath().normalize()
    }


    fun getOrPrepareRunDir(reportRunSignature: ReportRunSignature): Path {
        val dir = resolveRunDir(reportRunSignature)
        if (! Files.exists(dir)) {
            prepareRunDir(dir)
        }
        return dir
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


    fun readRunStatus(reportRunSignature: ReportRunSignature): OutputStatus {
        val dir = resolveRunDir(reportRunSignature)
        val infoFile = dir.resolve(reportInfoFile)
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


    private fun prepareRunDir(dir: Path) {
        Files.createDirectories(dir)

        val initialInfoYaml = """
            process-signature: "${WorkUtils.processSignature}"
            status: Running
        """.trimIndent()

        Files.write(
            dir.resolve(reportInfoFile),
            initialInfoYaml.toByteArray())
    }


    fun deleteDir(tempDir: Path) {
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete)
        }
        catch (e: Exception) {
            logger.error("Unable to cleanup", e)
            throw IOException(e)
        }
    }
}