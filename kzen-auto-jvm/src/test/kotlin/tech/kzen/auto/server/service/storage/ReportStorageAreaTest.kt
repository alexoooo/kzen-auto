package tech.kzen.auto.server.service.storage

import tech.kzen.auto.common.objects.document.report.output.OutputStatus
import tech.kzen.auto.server.objects.report.service.ReportWorkPool
import tech.kzen.auto.server.util.WorkUtils
import tech.kzen.lib.common.exec.logic.run.model.LogicExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunExecutionId
import tech.kzen.lib.common.exec.logic.run.model.LogicRunId
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class ReportStorageAreaTest {
    //-----------------------------------------------------------------------------------------------------------------
    private val workUtils = WorkUtils.temporary("report-storage-area-test")
    private val reportWorkPool = ReportWorkPool(workUtils)
    private val root: Path = workUtils.resolve(ReportWorkPool.defaultReportDir)
    private val area = ReportStorageArea(root, reportWorkPool)


    @AfterTest
    fun tearDown() {
        WorkUtils.recursivelyDeleteDir(workUtils.base())
    }


    private fun prepareRun(key: String): Path {
        val runDir = root.resolve(key)
        reportWorkPool.prepareRunDir(
            runDir,
            LogicRunExecutionId(LogicRunId("run"), LogicExecutionId("execution")))
        return runDir
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun runningRunOfThisProcessIsActiveAndNotDeletable() {
        prepareRun("running")

        val bundle = area.bundles().single()
        assertTrue(bundle.active)

        assertNotNull(area.deleteBundle("running"))
        assertTrue(Files.exists(root.resolve("running")))
    }


    @Test
    fun runningRunOfDeadProcessIsDeletable() {
        val runDir = root.resolve("killed")
        Files.createDirectories(runDir)
        Files.write(runDir.resolve("report.yaml"), """
            process-signature: "some-other-process"
            status: Running
            run-id: "run"
            execution-id: "execution"
        """.trimIndent().toByteArray())

        val bundle = area.bundles().single()
        assertFalse(bundle.active)

        assertNull(area.deleteBundle("killed"))
        assertFalse(Files.exists(runDir))
    }


    @Test
    fun settledRunIsDeletable() {
        val runDir = prepareRun("done")
        reportWorkPool.updateRunStatus(runDir, OutputStatus.Done)

        val bundle = area.bundles().single()
        assertFalse(bundle.active)

        assertNull(area.deleteBundle("done"))
        assertFalse(Files.exists(runDir))
    }


    @Test
    fun corruptRunInfoDoesNotBlockListingOrDeletion() {
        val runDir = root.resolve("corrupt")
        Files.createDirectories(runDir)
        Files.write(runDir.resolve("report.yaml"), "no-status-here: true".toByteArray())

        val bundle = area.bundles().single()
        assertFalse(bundle.active)

        assertNull(area.deleteBundle("corrupt"))
        assertFalse(Files.exists(runDir))
    }
}
