package tech.kzen.auto.server.service.target

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.server.service.vision.RgbGrid
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.util.ImmutableByteArray
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals


class TargetLocatorTest {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val white = 0xFFFFFF
        private const val red = 0xFF0000
        private const val blue = 0x0000FF
        private const val green = 0x00FF00
    }


    //-----------------------------------------------------------------------------------------------------------------
    private fun image(width: Int, height: Int, background: Int = white): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, background)
            }
        }
        return image
    }


    private fun BufferedImage.embed(crop: BufferedImage, originX: Int, originY: Int): BufferedImage {
        for (y in 0 until crop.height) {
            for (x in 0 until crop.width) {
                setRGB(originX + x, originY + y, crop.getRGB(x, y))
            }
        }
        return this
    }


    private fun crossCrop(size: Int, background: Int, line: Int): BufferedImage {
        val crop = image(size, size, background)
        for (i in 0 until size) {
            crop.setRGB(i, size / 2, line)
            crop.setRGB(size / 2, i, line)
        }
        return crop
    }


    private fun pngBytes(image: BufferedImage): ImmutableByteArray {
        val buffer = ByteArrayOutputStream()
        ImageIO.write(image, "png", buffer)
        return ImmutableByteArray.wrap(buffer.toByteArray())
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Three crops against one screenshot: many matches, one match, and no match — each keyed
     * to its own resource path (scan order within a crop is an implementation detail).
     */
    @Test
    fun matchesKeyedByCrop() {
        runBlocking {
            val documentPath = DocumentPath.parse("test/target-locator/~main.yaml")

            val manyCrop = crossCrop(3, red, blue)
            val singleCrop = crossCrop(3, blue, green)
            val absentCrop = crossCrop(3, green, red)

            val screenshot = image(40, 30)
                .embed(manyCrop, 1, 1)
                .embed(manyCrop, 20, 10)
                .embed(singleCrop, 10, 20)

            val notationMedia = MapNotationMedia()
            notationMedia.writeDocument(documentPath, "")
            for ((name, crop) in listOf(
                "many.png" to manyCrop,
                "single.png" to singleCrop,
                "absent.png" to absentCrop)
            ) {
                notationMedia.writeResource(
                    ResourceLocation(documentPath, ResourcePath.parse(name)),
                    pngBytes(crop))
            }

            val resourceListing = notationMedia.scan().documents[documentPath]!!.resources!!

            val target = TargetDocument(
                ObjectLocation(documentPath, NotationConventions.mainObjectPath),
                DocumentNotation(DocumentObjectNotation.empty, resourceListing))

            val located = TargetLocator(notationMedia)
                .locateAllByCrop(target, RgbGrid.ofImage(screenshot))

            assertEquals(
                mapOf(
                    ResourcePath.parse("many.png") to setOf(
                        Rectangle(1, 1, 3, 3),
                        Rectangle(20, 10, 3, 3)),
                    ResourcePath.parse("single.png") to setOf(
                        Rectangle(10, 20, 3, 3)),
                    ResourcePath.parse("absent.png") to setOf()),
                located.mapValues { it.value.toSet() })
        }
    }
}
