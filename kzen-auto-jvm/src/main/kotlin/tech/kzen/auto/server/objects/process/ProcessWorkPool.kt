package tech.kzen.auto.server.objects.process

import org.slf4j.LoggerFactory
import tech.kzen.auto.server.objects.process.model.ProcessRunSignature
import tech.kzen.auto.util.WorkUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.Path


object ProcessWorkPool {
    private val logger = LoggerFactory.getLogger(ProcessWorkPool::class.java)

    private const val oldDirLimit = 10
    private val dataProcessDir = Path.of("data-process")
    private val processInfoYamlFile = Path.of("data-process.yaml")


    fun resolveRunDir(runSignature: ProcessRunSignature): Path {
        val tempName = runSignature.digest().asString()
        val tempPath = dataProcessDir.resolve(tempName)
        val workDir = WorkUtils.resolve(tempPath)
        return workDir.toAbsolutePath().normalize()
    }


    fun getOrPrepareRunDir(runSignature: ProcessRunSignature): Path {
        val dir = resolveRunDir(runSignature)
        if (! Files.exists(dir)) {
            prepareRunDir(dir)
        }
        return dir
    }


    private fun prepareRunDir(dir: Path) {
        Files.createDirectories(dir)

        val initialInfoYaml = """
            process-signature: ${WorkUtils.processSignature}
            status: Working
        """.trimIndent()

        Files.write(
            dir.resolve(processInfoYamlFile),
            initialInfoYaml.toByteArray())
    }



    private fun deleteDir(tempDir: Path) {
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete)
        }
        catch (e: Exception) {
            logger.error("Unable to cleanup", e)
        }
    }
}