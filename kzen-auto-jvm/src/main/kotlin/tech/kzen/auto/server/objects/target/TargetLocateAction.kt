package tech.kzen.auto.server.objects.target

import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetLocateResult
import tech.kzen.auto.common.objects.document.target.TargetMatchRect
import tech.kzen.auto.common.paradigm.detached.DetachedAction
import tech.kzen.auto.server.service.target.TargetLocator
import tech.kzen.auto.server.service.vision.RgbGrid
import tech.kzen.lib.common.exec.ExecutionRequest
import tech.kzen.lib.common.exec.ExecutionResult
import tech.kzen.lib.common.exec.ExecutionSuccess
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.common.reflect.Service
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.store.LocalGraphStore
import tech.kzen.lib.platform.toInputStream
import javax.imageio.ImageIO


/**
 * Locates a Target document's crops in a client-provided screenshot (request body, PNG image):
 * every crop's matches, no uniqueness and no limit. The client sends the same bytes it displays,
 * so the returned rectangles are honest for that image by construction.
 */
@Reflect
class TargetLocateAction(
    @Service private val graphStore: LocalGraphStore,
    @Service private val targetLocator: TargetLocator
): DetachedAction {
    override suspend fun execute(
        request: ExecutionRequest
    ): ExecutionResult {
        val targetParam = request.getSingle(TargetDocument.paramTarget)
            ?: return ExecutionResult.failure("Target document missing: '${TargetDocument.paramTarget}'")
        val documentPath = DocumentPath.parse(targetParam)

        val screenshotPng = request.body
            ?: return ExecutionResult.failure("Screenshot missing (request body)")

        val documentNotation = graphStore.graphNotation().documents[documentPath]
            ?: return ExecutionResult.failure("Not found: $documentPath")

        if (!TargetDocument.isTarget(documentNotation)) {
            return ExecutionResult.failure("Not a Target document: $documentPath")
        }

        if (documentNotation.resources == null) {
            return ExecutionResult.failure("Target document has no resources: $documentPath")
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        val screenshotImage = ImageIO.read(screenshotPng.toInputStream())
            ?: return ExecutionResult.failure("Screenshot is not a readable image")
        val screenshotGrid = RgbGrid.ofImage(screenshotImage)

        val target = TargetDocument(
            ObjectLocation(documentPath, NotationConventions.mainObjectPath),
            documentNotation)

        val matchesByCrop = targetLocator.locateAllByCrop(target, screenshotGrid)

        val result = TargetLocateResult(
            screenshotGrid.width,
            screenshotGrid.height,
            matchesByCrop.mapValues { (_, matches) ->
                matches.map { TargetMatchRect(it.x, it.y, it.width, it.height) }
            })

        return ExecutionSuccess.ofValue(result.asExecutionValue())
    }
}
