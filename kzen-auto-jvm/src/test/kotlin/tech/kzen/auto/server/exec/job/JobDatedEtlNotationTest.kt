package tech.kzen.auto.server.exec.job

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.model.DataRef
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.data.binding.BindingName
import tech.kzen.auto.server.objects.job.value.JobDataValues
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue


class JobDatedEtlNotationTest {
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun logicSourceWorkerHostsThreeChildrenAndReadsBothRolesIntoPlainOutputs() {
        val root = Path.of("build/dated-sales").toAbsolutePath().normalize()
        root.toFile().deleteRecursively()
        val dates = listOf("2026-01-01", "2026-01-02", "2026-01-03")
        for ((index, date) in dates.withIndex()) {
            val main = root.resolve("input/$date/main.csv")
            val reference = root.resolve("ref/$date.csv")
            Files.createDirectories(main.parent)
            Files.createDirectories(reference.parent)
            Files.writeString(main, "value\n${index + 1}\n")
            Files.writeString(reference, "code\nR${index + 1}\n")
        }

        context = KzenAutoContext.forTest()
        val documentPath = DocumentPath.parse("test/datasource/logic/job-dated-etl-test.yaml")
        val jobLocation = ObjectLocation(documentPath, ObjectPath.parse("main"))
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        val jobLogic = JobLogicCompiler.compile(
            jobLocation,
            graphNotation,
            graphDefinition,
            LogicCompilerServices(
                context.graphEnvironment,
                context.objectStableMapper,
                context.cachedKotlinCompiler,
                context.scriptValidationCache,
                context.jobValidationCache,
                context.notationMetadataReader,
                context.jobWorkPool,
                LogicRunExecutionId.random()))
        val engine = RunEngine(jobLogic, context.objectStableMapper.objectStableId(jobLocation))

        val outcome = try {
            val completed = runBlocking {
                engine.resume()
                engine.await()
            }
            assertIs<Outcome.Success>(completed, "outcome: $completed")
            val runStableId = context.objectStableMapper.objectStableId(ObjectLocation(
                documentPath, ObjectPath.parse("main.workers/run")))
            val runNode = engine.snapshot().root.children.first { it.stableId == runStableId }
            assertEquals(3, runNode.children.size, "one child Job invocation per resolved unit")
            assertEquals(3, runNode.children.map { it.id }.toSet().size)
            val childStableId = context.objectStableMapper.objectStableId(ObjectLocation(
                DocumentPath.parse("test/datasource/logic/job-dated-etl-child-test.yaml"),
                ObjectPath.parse("main")))
            assertTrue(runNode.children.all { it.stableId == childStableId })
            completed
        }
        finally {
            engine.close()
        }

        val success = assertIs<Outcome.Success>(outcome, "outcome: $outcome")
        @Suppress("UNCHECKED_CAST")
        val refs = JobDataValues.boundary(
            success.value.requireValue(BindingName("outputs"))) as List<DataRef>
        assertEquals(dates, refs.map { Path.of(it.id).fileName.toString().removeSuffix(".csv") })
        for ((index, ref) in refs.withIndex()) {
            assertNull(ref.source)
            val path = Path.of(ref.id)
            assertEquals(Files.size(path).toString(), ref.attributes[DataRef.sizeKey])
            assertTrue(ref.attributes.containsKey(DataRef.modifiedKey))
            assertEquals(
                "date,value\n${dates[index]},${index + 1}\n",
                Files.readString(path))

            val reference = root.resolve("reference-output/${dates[index]}.csv")
            assertEquals(
                "code,observed\nR${index + 1},seen-R${index + 1}\n",
                Files.readString(reference),
                "the independent reference-role branch must execute")
        }
    }
}
