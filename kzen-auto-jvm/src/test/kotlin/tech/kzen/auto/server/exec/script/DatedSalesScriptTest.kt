package tech.kzen.auto.server.exec.script

import kotlinx.coroutines.runBlocking
import tech.kzen.auto.common.data.model.DataRole
import tech.kzen.auto.common.data.model.DataUnit
import tech.kzen.auto.server.context.KzenAutoContext
import tech.kzen.auto.server.exec.LogicCompilerServices
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.exec.engine.Outcome
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.tuple.TupleValue
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.obj.ObjectPath
import tech.kzen.lib.server.exec.engine.RunEngine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs


class DatedSalesScriptTest {
    private lateinit var context: KzenAutoContext


    @AfterTest
    fun tearDown() {
        if (::context.isInitialized) {
            context.close()
        }
    }


    @Test
    fun oneResultStepCompilesAndReturnsOrderedDatedUnits() {
        context = KzenAutoContext.forTest()
        val scriptLocation = ObjectLocation(
            DocumentPath.parse("test/datasource/logic/dated-sales-test.yaml"),
            ObjectPath.parse("main"))
        val graphNotation = AutoTestUtils.readNotation()
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(graphNotation).transitiveSuccessful
        val logic = ScriptLogicCompiler.compile(
            scriptLocation,
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
        val engine = RunEngine(
            logic,
            context.objectStableMapper.objectStableId(scriptLocation),
            TupleValue.empty)

        val outcome = try {
            runBlocking {
                engine.resume()
                engine.await()
            }
        }
        finally {
            engine.close()
        }

        @Suppress("UNCHECKED_CAST")
        val units = assertIs<Outcome.Success>(outcome, "outcome: $outcome")
            .value.mainComponentValue() as List<DataUnit>
        assertEquals(listOf("2026-01-01", "2026-01-02", "2026-01-03"), units.map { it.attributes["date"] })
        assertEquals(
            listOf(DataRole.main, DataRole("reference")),
            units.single { it.attributes["date"] == "2026-01-02" }.parts.map { it.role })
    }
}
