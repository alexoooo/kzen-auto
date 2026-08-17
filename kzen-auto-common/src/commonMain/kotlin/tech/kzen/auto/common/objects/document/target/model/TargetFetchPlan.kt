package tech.kzen.auto.common.objects.document.target.model


/**
 * What the target editor owes the server next, read off where its three fetch channels stand.
 *
 * The editor drives itself: a screenshot is acquired for the selected source, and once one is in hand it is
 * matched against the document's captured patches. Everything that invalidates a result — Refresh, a source
 * switch, a new document, an edited patch set — expresses itself by putting the owning channel back to
 * [TargetFetchPhase.Idle], so this function is the entire schedule and there is no second place a fetch starts.
 */
object TargetFetchPlan {
    //-----------------------------------------------------------------------------------------------------------------
    enum class Action {
        None,
        Screenshot,
        TraceScreenshots,
        Locate
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun next(
        source: TargetScreenshotSource,
        screenshot: TargetFetchPhase,
        trace: TargetFetchPhase,
        locate: TargetFetchPhase,
        hasCrops: Boolean
    ): Action {
        if (screenshot == TargetFetchPhase.Idle) {
            return when (source) {
                TargetScreenshotSource.Screen ->
                    Action.Screenshot

                // The browser source captures nothing of its own — its screenshot is a frame of the traced run's
                // strip, so the strip is what gets fetched, and only while the strip channel is itself idle.
                TargetScreenshotSource.Browser ->
                    when (trace) {
                        TargetFetchPhase.Idle -> Action.TraceScreenshots
                        else -> Action.None
                    }
            }
        }

        // Matching needs a screenshot to match against and at least one patch to match; with neither the locate
        // channel stays idle, so acquiring either one arms it.
        if (screenshot == TargetFetchPhase.Loaded && locate == TargetFetchPhase.Idle && hasCrops) {
            return Action.Locate
        }

        return Action.None
    }
}
