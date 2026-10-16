package tech.kzen.auto.server.data.design

import kotlinx.coroutines.delay
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.server.objects.job.JobValidationCache
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.util.digest.Digest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds


class DesignReadSessionTest {
    @Test
    fun observedReadIsEvidenceThatRechecksTheData() {
        var data = "a"
        var failing = false
        val session = DesignReadSession()

        val read = session.observe(
            "the data",
            { if (failing) error("gone") else data },
            { Digest.ofUtf8(it) })

        assertEquals("a", read)
        val evidence = session.evidence.single()
        assertEquals("the data", evidence.subject)
        assertTrue(evidence.unchanged())
        data = "b"
        assertFalse(evidence.unchanged())
        data = "a"
        failing = true
        assertFalse(evidence.unchanged(), "data that can no longer be read has changed")
    }


    @Test
    fun aSlowReadReadsToTheEnd() {
        val session = DesignReadSession()

        assertEquals("done", session.read { delay(3.seconds); "done" })
    }


    @Test
    fun cachedValidationIsReusedOnlyWhileItsEvidenceIsUnchanged() {
        val document = DocumentPath.parse("test/job/run/job-read-part-test.yaml")
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful
        val cache = JobValidationCache()
        var data = "a"
        var computed = 0
        val compute = { session: DesignReadSession ->
            computed += 1
            session.observe("the data", { data }, { Digest.ofUtf8(it) })
            JobValidation.empty
        }

        cache.jobValidation(document, graphDefinition, compute)
        cache.jobValidation(document, graphDefinition, compute)
        assertEquals(1, computed, "unchanged evidence reuses the validation")

        data = "b"
        cache.jobValidation(document, graphDefinition, compute)
        assertEquals(2, computed, "changed data validates again")
    }
}
