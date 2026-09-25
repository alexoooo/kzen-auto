package tech.kzen.auto.server.data.design

import kotlinx.coroutines.delay
import org.junit.Test
import tech.kzen.auto.common.objects.document.job.model.JobValidation
import tech.kzen.auto.server.objects.job.JobValidationCache
import tech.kzen.auto.server.util.AutoTestUtils
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.util.digest.Digest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue


class DesignReadSessionTest {
    @Test
    fun observedReadIsEvidenceThatRechecksTheData() {
        var data = "a"
        var failing = false
        val session = DesignReader().session(DesignReadBudget.editor)

        val read = session.observe(
            "the data",
            { if (failing) error("gone") else data },
            { Digest.ofUtf8(it) })

        assertEquals("a", assertNotNull(read).value)
        val evidence = session.evidence.single()
        assertEquals("the data", evidence.subject)
        assertTrue(evidence.unchanged())
        data = "b"
        assertFalse(evidence.unchanged())
        data = "a"
        failing = true
        assertFalse(evidence.unchanged(), "data that can no longer be read has changed")
        assertFalse(session.limited)
    }


    @Test
    fun theDeadlineCutsAReadShortAndLimitsThePass() {
        val session = DesignReader().session(DesignReadBudget(256, 50.milliseconds))

        assertNull(session.read { delay(5.seconds) })
        assertTrue(session.limited)
        assertNull(session.read { "after the deadline" }, "nothing is left of the deadline")
    }


    @Test
    fun noTimeLeftReadsNothing() {
        val session = DesignReader().session(DesignReadBudget(256, Duration.ZERO))
        var ran = false

        assertNull(session.read { ran = true })
        assertFalse(ran)
        assertTrue(session.limited)
    }


    @Test
    fun cachedValidationIsReusedOnlyWhileItsEvidenceIsUnchangedAndNeverWhenLimited() {
        val document = DocumentPath.parse("test/job/run/job-read-part-test.yaml")
        val graphDefinition = AutoTestUtils.graphDefinitionAttempt(AutoTestUtils.readNotation()).transitiveSuccessful
        val cache = JobValidationCache()
        var data = "a"
        var limit = false
        var computed = 0
        val compute = { session: DesignReadSession ->
            computed += 1
            session.observe("the data", { data }, { Digest.ofUtf8(it) })
            if (limit) {
                session.read { delay(5.seconds) }
            }
            JobValidation.empty
        }
        val budget = DesignReadBudget(256, 200.milliseconds)

        cache.jobValidation(document, graphDefinition, budget, compute)
        cache.jobValidation(document, graphDefinition, budget, compute)
        assertEquals(1, computed, "unchanged evidence reuses the validation")

        data = "b"
        cache.jobValidation(document, graphDefinition, budget, compute)
        assertEquals(2, computed, "changed data validates again")

        limit = true
        data = "c"
        cache.jobValidation(document, graphDefinition, budget, compute)
        cache.jobValidation(document, graphDefinition, budget, compute)
        assertEquals(4, computed, "a pass the deadline cut short is never reused")
    }
}
