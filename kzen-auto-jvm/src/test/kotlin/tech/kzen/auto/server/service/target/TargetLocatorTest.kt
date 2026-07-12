package tech.kzen.auto.server.service.target

import kotlinx.coroutines.runBlocking
import org.junit.Test
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetMatchPolicy
import tech.kzen.auto.server.service.vision.RgbGrid
import tech.kzen.auto.server.service.vision.TemplateMatcher
import tech.kzen.lib.common.model.document.DocumentPath
import tech.kzen.lib.common.model.location.ObjectLocation
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.notation.DocumentNotation
import tech.kzen.lib.common.model.structure.notation.DocumentObjectNotation
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.media.MapNotationMedia
import tech.kzen.lib.common.service.notation.NotationConventions
import tech.kzen.lib.common.service.parse.YamlNotationParser
import tech.kzen.lib.common.util.ImmutableByteArray
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue


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
                located.mapValues { it.value.matches.map { match -> match.rect }.toSet() })

            // Exact matches carry score/scale 1.0, and without a tolerance there is no
            // closest-rejected diagnostic
            assertTrue(located.values.flatMap { it.matches }.all { it.score == 1.0 && it.scale == 1.0 })
            assertTrue(located.values.all { it.bestRejected == null })

            assertEquals(
                mapOf(
                    ResourcePath.parse("many.png") to (3 to 3),
                    ResourcePath.parse("single.png") to (3 to 3),
                    ResourcePath.parse("absent.png") to (3 to 3)),
                located.mapValues { it.value.cropWidth to it.value.cropHeight })
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * `tolerance: 0.8` on the main object turns on the score-based fallback: a brightness-shifted
     * rendering (exact miss) is found with its score, and an absent crop reports its
     * closest-rejected candidate instead of silence.
     */
    @Test
    fun toleranceEnablesScoredFallback() {
        runBlocking {
            val documentPath = DocumentPath.parse("test/target-tolerance/~main.yaml")

            val size = 8
            val crop = crossCrop(size, red, blue)

            // Same pattern with a few pixels perturbed (non-affine): breaks pixel equality
            // and caps the correlation below 1, but keeps it comfortably above the tolerance
            val shifted = crossCrop(size, red, blue)
            shifted.setRGB(1, 1, 0xC04040)
            shifted.setRGB(6, 2, 0xC04040)

            val screenshot = image(60, 40)
                .embed(shifted, 12, 9)

            val notationMedia = MapNotationMedia()
            notationMedia.writeDocument(
                documentPath,
                "main:\n" +
                "  is: Target\n" +
                "  tolerance: 0.8")
            notationMedia.writeResource(
                ResourceLocation(documentPath, ResourcePath.parse("shifted.png")),
                pngBytes(crop))

            val scan = notationMedia.scan().documents[documentPath]!!
            val resourceListing = scan.resources!!

            val parser = YamlNotationParser()
            val documentObjects = parser.parseDocumentObjects(
                notationMedia.readDocument(documentPath))

            val documentNotation = DocumentNotation(documentObjects, resourceListing)
            assertEquals(0.8, TargetDocument.tolerance(documentNotation))

            val target = TargetDocument(
                ObjectLocation(documentPath, NotationConventions.mainObjectPath),
                documentNotation)

            val located = TargetLocator(notationMedia)
                .locateAllByCrop(target, RgbGrid.ofImage(screenshot))

            val cropMatches = located[ResourcePath.parse("shifted.png")]!!
            assertEquals(1, cropMatches.matches.size, "expected single match: ${cropMatches.matches}")
            val match = cropMatches.matches.single()
            assertEquals(Rectangle(12, 9, size, size), match.rect)
            assertTrue(match.score >= 0.8 && match.score < 1.0, "unexpected score: ${match.score}")
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun notFoundMessageNamesEachCropWithDimensions() {
        val message = TargetLocator.notFoundMessage(
            linkedMapOf(
                ResourcePath.parse("desktop.png") to
                        TargetLocator.CropMatches(21, 17, listOf()),
                ResourcePath.parse("browser.png") to
                        TargetLocator.CropMatches(16, 11, listOf())),
            2560, 1440)

        assertEquals(
            "Target not found in 2560x1440 screenshot: " +
                    "desktop.png (21x17) no match, browser.png (16x11) no match",
            message)
    }


    @Test
    fun notFoundMessageReportsClosestRejectedCandidate() {
        val message = TargetLocator.notFoundMessage(
            linkedMapOf(
                ResourcePath.parse("desktop.png") to
                        TargetLocator.CropMatches(21, 17, listOf(),
                            TemplateMatcher.ScoredMatch(Rectangle(412, 300, 21, 17), 0.912, 1.0))),
            2560, 1440)

        assertEquals(
            "Target not found in 2560x1440 screenshot: " +
                    "desktop.png (21x17) no match (closest 0.91 at [412, 300])",
            message)
    }


    @Test
    fun ambiguousMessageListsMatchesAndExcludedPreviews() {
        val message = TargetLocator.ambiguousMessage(
            listOf(
                TargetLocator.CropMatch(
                    ResourcePath.parse("desktop.png"),
                    TemplateMatcher.ScoredMatch(Rectangle(10, 20, 21, 17), 1.0, 1.0)),
                TargetLocator.CropMatch(
                    ResourcePath.parse("browser.png"),
                    TemplateMatcher.ScoredMatch(Rectangle(50, 60, 16, 11), 0.834, 1.0))),
            1, 2560, 1440)

        assertEquals(
            "More than one target found in 2560x1440 screenshot: " +
                    "desktop.png at [10, 20], browser.png at [50, 60] (score 0.83) " +
                    "(1 preview of this target excluded)",
            message)
    }


    @Test
    fun matchNoteReportsCropAndLocation() {
        val note = TargetLocator.matchNote(
            TargetLocator.CropMatch(
                ResourcePath.parse("desktop.png"),
                TemplateMatcher.ScoredMatch(Rectangle(10, 20, 21, 17), 1.0, 1.0)),
            0)

        assertEquals("Matched desktop.png (21x17) at [10, 20]", note)
    }


    @Test
    fun matchNoteReportsScoreAndScaleForTolerantMatches() {
        val note = TargetLocator.matchNote(
            TargetLocator.CropMatch(
                ResourcePath.parse("desktop.png"),
                TemplateMatcher.ScoredMatch(Rectangle(10, 20, 32, 26), 0.851, 1.5)),
            0)

        assertEquals("Matched desktop.png (32x26) at [10, 20] (score 0.85 at scale 1.5)", note)
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun policySelectionOverOrderedCandidates() {
        val candidates = listOf("a", "b", "c")

        assertEquals(
            TargetLocator.PolicySelection.Rejected("More than one target found (3)"),
            TargetLocator.selectByPolicy(candidates, TargetMatchPolicy.Unique))
        assertEquals(
            TargetLocator.PolicySelection.Selected("a"),
            TargetLocator.selectByPolicy(listOf("a"), TargetMatchPolicy.Unique))

        assertEquals(
            TargetLocator.PolicySelection.Selected("a"),
            TargetLocator.selectByPolicy(candidates, TargetMatchPolicy.First))

        assertEquals(
            TargetLocator.PolicySelection.Selected("c"),
            TargetLocator.selectByPolicy(candidates, TargetMatchPolicy.Nth(2)))
        assertEquals(
            TargetLocator.PolicySelection.Rejected("Target index 3 out of range (3 matches)"),
            TargetLocator.selectByPolicy(candidates, TargetMatchPolicy.Nth(3)))

        // Best over pre-ordered candidates is the first (score-ranked callers sort first)
        assertEquals(
            TargetLocator.PolicySelection.Selected("a"),
            TargetLocator.selectByPolicy(candidates, TargetMatchPolicy.Best))

        assertEquals(
            TargetLocator.PolicySelection.Rejected("Target not found"),
            TargetLocator.selectByPolicy(listOf<String>(), TargetMatchPolicy.First))
    }


    @Test
    fun onlyPreviewsMessageCountsExclusions() {
        val message = TargetLocator.onlyPreviewsMessage(2, 2560, 1440)

        assertEquals(
            "Target not found in 2560x1440 screenshot: " +
                    "the only matches (2) are previews of this target in the automated page",
            message)
    }
}
