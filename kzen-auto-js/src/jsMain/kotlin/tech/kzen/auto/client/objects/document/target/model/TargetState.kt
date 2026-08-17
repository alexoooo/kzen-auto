package tech.kzen.auto.client.objects.document.target.model

import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.model.TargetFetch
import tech.kzen.auto.common.objects.document.target.model.TargetScreenshotSource
import tech.kzen.lib.common.exec.BinaryValue
import tech.kzen.lib.common.exec.RequestParams
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.resource.ResourceListing


/**
 * Everything the Target document's editor shows: the document it is editing, the two capture settings the user
 * chooses, and the three fetch channels [TargetStore] drives.
 *
 * The document is held as its own notation rather than as the whole graph structure, so a publish elsewhere in
 * the graph compares cheaply and the derived reads below stay one map lookup each.
 */
data class TargetState(
    val documentPath: DocumentPath,
    val parameters: RequestParams,
    val documentNotation: DocumentNotation,

    val source: TargetScreenshotSource,
    val captureDelaySeconds: Int,

    val screenshot: TargetFetch<TargetScreenshot>,
    val trace: TargetFetch<List<BinaryValue>>,
    val locate: TargetFetch<TargetLocateResult>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun initial(
            documentPath: DocumentPath,
            parameters: RequestParams,
            documentNotation: DocumentNotation
        ): TargetState {
            return TargetState(
                documentPath = documentPath,
                parameters = parameters,
                documentNotation = documentNotation,
                source = TargetScreenshotSource.Screen,
                captureDelaySeconds = 0,
                screenshot = TargetFetch.Idle,
                trace = TargetFetch.Idle,
                locate = TargetFetch.Idle)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    val resources: ResourceListing?
        get() = documentNotation.resources

    val hasCrops: Boolean
        get() = TargetDocument.hasCrops(documentNotation)

    val tolerance: Double?
        get() = TargetDocument.tolerance(documentNotation)


    //-----------------------------------------------------------------------------------------------------------------
    /** Every channel back to square one — what Refresh, and any change of what is being looked at, amounts to. */
    fun rearmed(): TargetState {
        return copy(
            screenshot = TargetFetch.Idle,
            trace = TargetFetch.Idle,
            locate = TargetFetch.Idle)
    }


    /**
     * Whether this state puts a channel back to [TargetFetch.Idle] that [previous] had already moved off it —
     * i.e. whether it re-arms a fetch that may still be in flight. [TargetStore] answers that with its epoch.
     */
    fun rearms(previous: TargetState): Boolean {
        return screenshot == TargetFetch.Idle && previous.screenshot != TargetFetch.Idle ||
                trace == TargetFetch.Idle && previous.trace != TargetFetch.Idle ||
                locate == TargetFetch.Idle && previous.locate != TargetFetch.Idle
    }
}
