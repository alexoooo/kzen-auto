package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertIs


/** Runs both File authoring surfaces through compiled Job graphs. */
class JobReadNotationTest {
    private val directory = Path.of("build/job-read")
    private lateinit var context: KzenAutoContext


    @After
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun nominalSourceReadsAndWritesThroughRealRunGraph() {
        runFixture("job-read-test.yaml", "input.csv", "output.csv")
    }


    @Test
    fun inlineFileWorkerReadsAndWritesThroughRealRunGraph() {
        runFixture("job-file-source-worker-test.yaml", "inline-input.csv", "inline-output.csv")
    }


    private fun runFixture(documentName: String, inputName: String, outputName: String) {
        val input = directory.resolve(inputName)
        val output = directory.resolve(outputName)
        val lines = listOf("name,value", "alpha,1", "beta,2")
        Files.createDirectories(directory)
        Files.writeString(input, lines.joinToString(separator = "\n", postfix = "\n"))
        Files.deleteIfExists(output)

        val documentPath = DocumentPath.parse("test/job/run/$documentName")
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        context = KzenAutoContext.forTest()
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        val logic = JobLogicCompiler.compile(
            jobLocation, graphNotation, graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
        val engine = RunEngine(logic, context.objectStableMapper.objectStableId(jobLocation))

        val outcome = try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }

        assertIs<Outcome.Success>(outcome)
        assertEquals(lines, Files.readAllLines(output))
    }
}
