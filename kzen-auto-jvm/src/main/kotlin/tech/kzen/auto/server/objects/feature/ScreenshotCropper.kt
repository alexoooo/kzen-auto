package tech.kzen.auto.server.objects.feature

import tech.kzen.auto.common.objects.document.feature.FeatureDocument
import tech.kzen.auto.common.paradigm.common.model.*
import tech.kzen.auto.common.paradigm.detached.api.DetachedAction
import tech.kzen.auto.common.paradigm.detached.model.DetachedRequest
import tech.kzen.lib.platform.toInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO


@Suppress("unused")
class ScreenshotCropper: DetachedAction {
    override suspend fun execute(
            request: DetachedRequest
    ): ExecutionResult {
        val x = request.getInt(FeatureDocument.cropLeftParam)
                ?: return ExecutionFailure("Missing parameter: ${FeatureDocument.cropLeftParam}")

        val y = request.getInt(FeatureDocument.cropTopParam)
                ?: return ExecutionFailure("Missing parameter: ${FeatureDocument.cropTopParam}")

        val width = request.getInt(FeatureDocument.cropWidthParam)
                ?: return ExecutionFailure("Missing parameter: ${FeatureDocument.cropWidthParam}")

        val height = request.getInt(FeatureDocument.cropHeightParam)
                ?: return ExecutionFailure("Missing parameter: ${FeatureDocument.cropHeightParam}")

        val body = request.body
                ?: return ExecutionFailure("Missing body")

        val image = ImageIO.read(body.toInputStream())
        val crop = image.getSubimage(x, y, width, height)

        val buffer = ByteArrayOutputStream()
        ImageIO.write(crop, "png", buffer)
        val cropPng = buffer.toByteArray()

        return ExecutionSuccess(
                BinaryExecutionValue(cropPng),
                NullExecutionValue)
    }
}