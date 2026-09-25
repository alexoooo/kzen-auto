package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.data.design.DesignReadBudget
import tech.kzen.auto.server.data.design.DesignReader
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.objects.job.JobValidator
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.data.type.DataType
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull


/**
 * A Job typed from its data before Run (docs/plans/2026-09-24_values-metadata-and-design-time-types.md, R5/R6):
 * `File` offers the files it selects, `Parse` types itself from what they hold, and an expression below checks
 * against that type; a run validates again at its start and fails by name when the data no longer fits.
 */
class JobDesignTimeTypeTest {
    private val document = DocumentPath.parse("test/job/run/job-design-time-test.yaml")
    private val directory = Path.of("build/job-design-time")
    private val input = directory.resolve("in")
    private lateinit var context: KzenAutoContext


    @Before
    fun setUp() {
        Files.createDirectories(input)
        Files.list(input).use { files -> files.forEach(Files::delete) }
        Files.deleteIfExists(directory.resolve("out.csv"))
        Files.writeString(input.resolve("a.csv"), "name,value\nalpha,1\n")
        Files.writeString(input.resolve("b.csv"), "name,value,extra\nbeta,2,x\n")
        context = KzenAutoContext.forTest()
    }


    @After
    fun tearDown() {
        context.close()
    }


    @Test
    fun parseTypesItselfFromTheSelectedFilesAndTheFilterChecksAgainstIt() {
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful
        val validation = JobValidator.validateDetached(
            document, graphDefinition, context.notationMetadataReader, context.graphEnvironment,
            DesignReader().session(DesignReadBudget.editor))

        val parse = assertNotNull(validation.workerValidations[ObjectPath.parse("main.workers/parse")])
        assertNull(parse.errorMessage)
        assertEquals("Inferred from 2 of 2 values", parse.provenance)
        val record = assertIs<DataType.Record>(assertNotNull(parse.contract).payload().structural)
        assertEquals(listOf("name", "value", "extra"), record.fields.map { it.id.name })
        assertNull(validation.workerValidations[ObjectPath.parse("main.workers/filter")]?.errorMessage)
    }


    @Test
    fun runReadsWithTheTypeValidatedAtItsStart() {
        val outcome = run()

        assertIs<Outcome.Success>(outcome)
        val lines = Files.readAllLines(directory.resolve("out.csv"))
        assertEquals("name,value,extra", lines.first())
        assertEquals(listOf("beta,2,x"), lines.drop(1))
    }


    @Test
    fun runFailsByNameWhenTheDataNoLongerHasAFieldAnExpressionUses() {
        Files.delete(input.resolve("b.csv"))

        val outcome = run()

        val failed = assertIs<Outcome.Failed>(outcome)
        assertContains(failed.message, "extra")
    }


    private fun run(): Outcome {
        val jobLocation = ObjectLocation(document, ObjectPath.parse("main"))
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
