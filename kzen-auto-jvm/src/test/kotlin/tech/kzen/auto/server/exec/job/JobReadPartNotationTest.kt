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


/** End-to-end coverage of Read(units) -> ReadPart, directly and across a hosted child-Job boundary. */
class JobReadPartNotationTest {
    private val directory = Path.of("build/job-read-part")
    private lateinit var context: KzenAutoContext


    @After
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun readUnitsThenReadPartConcatenatesTwoRealCsvFiles() {
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("input-a.csv"), "name,value\nalpha,1\nbeta,2\n")
        Files.writeString(directory.resolve("input-b.csv"), "name,value\ngamma,3\n")
        val output = directory.resolve("output.csv")
        Files.deleteIfExists(output)

        val outcome = run("test/job/run/job-read-part-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(
            listOf("name,value", "alpha,1", "beta,2", "gamma,3"),
            Files.readAllLines(output))
    }


    @Test
    fun hostedChildReadsDataUnitParameterWithoutExpressionIo() {
        Files.createDirectories(directory)
        val lines = listOf("name,value", "child,7", "boundary,8")
        Files.writeString(
            directory.resolve("child-input.csv"),
            lines.joinToString(separator = "\n", postfix = "\n"))
        val output = directory.resolve("child-output.csv")
        Files.deleteIfExists(output)

        val outcome = run("test/job/run/job-read-part-child-host-test.yaml")

        assertIs<Outcome.Success>(outcome)
        assertEquals(lines, Files.readAllLines(output))
    }


    private fun run(document: String): Outcome {
        context = KzenAutoContext.forTest()
        val jobLocation = ObjectLocation(DocumentPath.parse(document), ObjectPath.parse("main"))
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
        return try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }
    }
}
