package tech.kzen.auto.common.objects.document.target.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals


class TargetFetchPlanTest {
    //-----------------------------------------------------------------------------------------------------------------
    private fun next(
        source: TargetScreenshotSource = TargetScreenshotSource.Screen,
        screenshot: TargetFetchPhase = TargetFetchPhase.Idle,
        trace: TargetFetchPhase = TargetFetchPhase.Idle,
        locate: TargetFetchPhase = TargetFetchPhase.Idle,
        hasCrops: Boolean = true
    ): TargetFetchPlan.Action {
        return TargetFetchPlan.next(source, screenshot, trace, locate, hasCrops)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun idleScreenSourceCaptures() {
        assertEquals(
            TargetFetchPlan.Action.Screenshot,
            next(source = TargetScreenshotSource.Screen))
    }


    @Test
    fun idleBrowserSourceFetchesStripFirst() {
        assertEquals(
            TargetFetchPlan.Action.TraceScreenshots,
            next(source = TargetScreenshotSource.Browser))
    }


    @Test
    fun requestedScreenshotIsNotRequestedAgain() {
        for (source in TargetScreenshotSource.entries) {
            assertEquals(
                TargetFetchPlan.Action.None,
                next(source = source, screenshot = TargetFetchPhase.Requesting))
        }
    }


    @Test
    fun failedScreenshotIsNotRetried() {
        assertEquals(
            TargetFetchPlan.Action.None,
            next(screenshot = TargetFetchPhase.Failed))
    }


    @Test
    fun stripIsFetchedOnlyWhileItsChannelIsIdle() {
        for (trace in TargetFetchPhase.entries) {
            val expected = when (trace) {
                TargetFetchPhase.Idle -> TargetFetchPlan.Action.TraceScreenshots
                else -> TargetFetchPlan.Action.None
            }

            assertEquals(
                expected,
                next(source = TargetScreenshotSource.Browser, trace = trace))
        }
    }


    @Test
    fun loadedScreenshotWithPatchesMatches() {
        assertEquals(
            TargetFetchPlan.Action.Locate,
            next(screenshot = TargetFetchPhase.Loaded))
    }


    @Test
    fun matchWaitsForTheFirstPatch() {
        assertEquals(
            TargetFetchPlan.Action.None,
            next(screenshot = TargetFetchPhase.Loaded, hasCrops = false))
    }


    @Test
    fun settledMatchIsNotReissued() {
        for (locate in listOf(TargetFetchPhase.Requesting, TargetFetchPhase.Loaded, TargetFetchPhase.Failed)) {
            assertEquals(
                TargetFetchPlan.Action.None,
                next(screenshot = TargetFetchPhase.Loaded, locate = locate))
        }
    }


    @Test
    fun matchingNeverPreemptsAcquiringAScreenshot() {
        for (screenshot in listOf(TargetFetchPhase.Idle, TargetFetchPhase.Requesting, TargetFetchPhase.Failed)) {
            assertNotEquals(
                TargetFetchPlan.Action.Locate,
                next(screenshot = screenshot))
        }
    }
}
