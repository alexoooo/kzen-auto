package tech.kzen.auto.server.service.target

import org.openqa.selenium.OutputType
import org.openqa.selenium.WebElement
import org.openqa.selenium.remote.RemoteWebDriver
import tech.kzen.auto.common.objects.document.target.TargetDocument
import tech.kzen.auto.common.objects.document.target.TargetMatchPolicy
import tech.kzen.auto.common.objects.document.target.TargetSpec
import tech.kzen.auto.server.service.vision.RgbGrid
import tech.kzen.auto.server.service.vision.TemplateMatcher
import tech.kzen.lib.common.model.location.ResourceLocation
import tech.kzen.lib.common.model.structure.resource.ResourcePath
import tech.kzen.lib.common.service.media.NotationMedia
import tech.kzen.lib.common.util.digest.Digest
import tech.kzen.lib.platform.toInputStream
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.math.roundToInt


/**
 * Locates action targets in the browser under automation: dispatches each [TargetSpec] to the
 * registered [TargetTypeLocator] that can locate it, and owns the shared machinery — visual
 * matching over a Target document's crops (tolerance, preview exclusion, per-crop diagnostics)
 * and [selectByPolicy].
 */
class TargetLocator(
    private val notationMedia: NotationMedia,
    builtinLocators: List<TargetTypeLocator> = defaultLocators
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private const val cropGridCacheMaxEntries = 64
        private const val cropGridCacheLoadFactor = 0.75f

        // Per-crop scan cap: uniqueness only needs 2, but the error detail lists the ambiguity and
        // preview exclusion needs surviving candidates, so keep a few more
        private const val diagnosticMatchLimit = 8

        private val defaultLocators = listOf(
            FocusTargetLocator,
            TextTargetLocator,
            XpathTargetLocator,
            VisualTargetLocator)


        /**
         * Resolve a [TargetMatchPolicy] over ordered [candidates]. [Best][TargetMatchPolicy.Best]
         * selects the first candidate — callers with scores order by score first; others treat
         * it as First (see the policy's contract).
         */
        fun <T> selectByPolicy(
            candidates: List<T>,
            policy: TargetMatchPolicy
        ): PolicySelection<T> {
            if (candidates.isEmpty()) {
                return PolicySelection.Rejected("Target not found")
            }

            return when (policy) {
                TargetMatchPolicy.Unique ->
                    if (candidates.size == 1) {
                        PolicySelection.Selected(candidates.single())
                    }
                    else {
                        PolicySelection.Rejected(
                            "More than one target found (${candidates.size})")
                    }

                TargetMatchPolicy.First, TargetMatchPolicy.Best ->
                    PolicySelection.Selected(candidates.first())

                is TargetMatchPolicy.Nth ->
                    if (policy.index in candidates.indices) {
                        PolicySelection.Selected(candidates[policy.index])
                    }
                    else {
                        PolicySelection.Rejected(
                            "Target index ${policy.index} out of range (${candidates.size} matches)")
                    }
            }
        }


        fun notFoundMessage(
            matchesByCrop: Map<ResourcePath, CropMatches>,
            screenshotWidth: Int,
            screenshotHeight: Int
        ): String {
            val perCrop = matchesByCrop.entries.joinToString(", ") { (resourcePath, cropMatches) ->
                val closest = cropMatches.bestRejected?.let {
                    " (closest ${formatScore(it.score)} at [${it.rect.x}, ${it.rect.y}])"
                } ?: ""
                "${resourcePath.asString()} (${cropMatches.cropWidth}x${cropMatches.cropHeight}) " +
                        "no match$closest"
            }
            return "Target not found in ${screenshotWidth}x$screenshotHeight screenshot: $perCrop"
        }


        fun onlyPreviewsMessage(
            previewCount: Int,
            screenshotWidth: Int,
            screenshotHeight: Int
        ): String {
            return "Target not found in ${screenshotWidth}x$screenshotHeight screenshot: " +
                    "the only matches ($previewCount) are previews of this target in the automated page"
        }


        fun ambiguousMessage(
            matches: List<CropMatch>,
            previewCount: Int,
            screenshotWidth: Int,
            screenshotHeight: Int
        ): String {
            val listing = matches.joinToString(", ") {
                val scoreSuffix =
                    if (it.match.score < TargetDocument.exactTolerance) {
                        " (score ${formatScore(it.match.score)})"
                    }
                    else {
                        ""
                    }
                "${it.resourcePath.asString()} at [${it.rect.x}, ${it.rect.y}]$scoreSuffix"
            }
            return "More than one target found in ${screenshotWidth}x$screenshotHeight screenshot: " +
                    listing + previewSuffix(previewCount)
        }


        fun matchNote(
            match: CropMatch,
            previewCount: Int
        ): String {
            val tolerant = match.match.score < TargetDocument.exactTolerance
            val scoreSuffix =
                if (!tolerant) { "" }
                else if (match.match.scale == 1.0) { " (score ${formatScore(match.match.score)})" }
                else { " (score ${formatScore(match.match.score)} at scale ${match.match.scale})" }

            return "Matched ${match.resourcePath.asString()} " +
                    "(${match.rect.width}x${match.rect.height}) at [${match.rect.x}, ${match.rect.y}]" +
                    scoreSuffix +
                    previewSuffix(previewCount)
        }


        private fun previewSuffix(previewCount: Int): String {
            if (previewCount == 0) {
                return ""
            }
            val plural = if (previewCount == 1) { "preview" } else { "previews" }
            return " ($previewCount $plural of this target excluded)"
        }


        private fun formatScore(score: Double): String {
            return ((score * 100).roundToInt() / 100.0).toString()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    data class Result(
        val webElement: WebElement?,
        val error: String?,

        // Success diagnostic: which crop matched and where (see [matchNote]); null on error
        // and for target kinds that don't involve visual matching
        val note: String? = null
    ) {
        init {
            require(webElement == null && error != null ||
                    webElement != null && error == null)
        }

        fun isError(): Boolean {
            return error != null
        }
    }


    /**
     * One crop's scan against a screenshot: the crop's own dimensions plus every match found
     * (exact matches carry score/scale 1.0). When tolerant matching ran and found nothing,
     * [bestRejected] is the closest-scoring candidate — the "how close was it" diagnostic.
     */
    data class CropMatches(
        val cropWidth: Int,
        val cropHeight: Int,
        val matches: List<TemplateMatcher.ScoredMatch>,
        val bestRejected: TemplateMatcher.ScoredMatch? = null
    )


    /** A single match with its crop attribution. */
    data class CropMatch(
        val resourcePath: ResourcePath,
        val match: TemplateMatcher.ScoredMatch
    ) {
        val rect: Rectangle get() = match.rect
    }


    sealed class PolicySelection<out T> {
        data class Selected<T>(
            val candidate: T
        ): PolicySelection<T>()

        data class Rejected(
            val reason: String
        ): PolicySelection<Nothing>()
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Keyed by content digest: a re-captured crop has a new digest, so stale pixels can never
     * be served.
     */
    private val cropGridCache = object: LinkedHashMap<Digest, RgbGrid>(
        cropGridCacheMaxEntries, cropGridCacheLoadFactor, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Digest, RgbGrid>): Boolean {
            return size > cropGridCacheMaxEntries
        }
    }


    // Registration happens at module initialization, locates on run coroutines — copy-on-write
    // keeps the lookup lock-free
    private val locators = CopyOnWriteArrayList(builtinLocators)


    //-----------------------------------------------------------------------------------------------------------------
    /** A third-party target type contributes its locate handling here (from its module's
     *  initialization) — no shared-file edit; see [TargetTypeLocator]. */
    fun register(locator: TargetTypeLocator) {
        locators.add(locator)
    }


    suspend fun locateElement(
        target: TargetSpec,
        driver: RemoteWebDriver
    ): Result {
        val locator = locators.firstOrNull { it.canLocate(target) }
            ?: return Result(null, "No locator registered for target: $target")

        return locator.locate(target, driver, this)
    }


    suspend fun locateElement(
        target: TargetDocument,
        driver: RemoteWebDriver,
        policy: TargetMatchPolicy = TargetMatchPolicy.Unique
    ): Result {
        val screenshotPngBytes = driver.getScreenshotAs(OutputType.BYTES)
        val screenshotImage = ImageIO.read(ByteArrayInputStream(screenshotPngBytes))
        val screenshotGrid = RgbGrid.ofImage(screenshotImage)

        val matchesByCrop = locateAllByCrop(target, screenshotGrid, diagnosticMatchLimit)

        val allMatches = matchesByCrop.flatMap { (resourcePath, cropMatches) ->
            cropMatches.matches.map { CropMatch(resourcePath, it) }
        }

        if (allMatches.isEmpty()) {
            return Result(null, notFoundMessage(
                matchesByCrop, screenshotGrid.width, screenshotGrid.height))
        }

        val cssScale = screenshotCssScale(screenshotGrid.width, driver)
        val elementByMatch = allMatches.associateWith {
            elementAt(it.rect, cssScale, driver)
        }

        // A script automating the kzen-auto UI itself can see previews of its own targets
        // (a step's target thumbnail, the Target document's crop list / capture surfaces);
        // those are marked in the DOM and never count as matches.
        val (previews, candidates) = allMatches.partition {
            val element = elementByMatch[it]
            element != null && isTargetPreview(element, driver)
        }

        if (candidates.isEmpty()) {
            return Result(null, onlyPreviewsMessage(
                previews.size, screenshotGrid.width, screenshotGrid.height))
        }

        // Deterministic candidate order: top-to-bottom then left-to-right; Best ranks by score first
        val ordered =
            when (policy) {
                TargetMatchPolicy.Best ->
                    candidates.sortedWith(
                        compareByDescending<CropMatch> { it.match.score }
                            .thenBy { it.rect.y }
                            .thenBy { it.rect.x })

                else ->
                    candidates.sortedWith(
                        compareBy({ it.rect.y }, { it.rect.x }))
            }

        val match =
            when (val selection = selectByPolicy(ordered, policy)) {
                is PolicySelection.Selected ->
                    selection.candidate

                is PolicySelection.Rejected ->
                    return Result(null,
                        if (policy == TargetMatchPolicy.Unique) {
                            ambiguousMessage(ordered, previews.size,
                                screenshotGrid.width, screenshotGrid.height)
                        }
                        else {
                            "${selection.reason} in " +
                                    "${screenshotGrid.width}x${screenshotGrid.height} screenshot"
                        })
            }

        val element = elementByMatch[match]
            ?: return Result(null,
                "No element at match [${match.rect.centerX.roundToInt()}, ${match.rect.centerY.roundToInt()}] " +
                "(screenshot ${screenshotGrid.width}x${screenshotGrid.height}, CSS scale $cssScale)")

        return Result(element, null, matchNote(match, previews.size))
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Every crop's matches (up to [limitPerCrop] each), keyed by resource path — no uniqueness:
     * the caller decides what multiple matches mean (preview overlay shows everything,
     * the click path reports them as an ambiguity).
     *
     * Exact matching always runs first (score/scale 1.0). When it finds nothing for a crop and
     * the document declares a tolerance below [TargetDocument.exactTolerance], score-based
     * multi-scale matching takes over (see [TemplateMatcher.locateScored]).
     */
    suspend fun locateAllByCrop(
        target: TargetDocument,
        screenshotGrid: RgbGrid,
        limitPerCrop: Int = Int.MAX_VALUE
    ): Map<ResourcePath, CropMatches> {
        val documentPath = target.objectLocation.documentPath
        val resourceListing = target.documentNotation.resources
            ?: error("Target document has no resources: $documentPath")

        val tolerance = TargetDocument.tolerance(target.documentNotation)
        val tolerantThreshold =
            if (tolerance != null && tolerance < TargetDocument.exactTolerance) {
                tolerance
            }
            else {
                null
            }

        val sourceHistogram = TemplateMatcher.quantizedColorHistogram(screenshotGrid)

        // Computed at most once, only when some crop needs the tolerant path
        var sourceLuminance: TemplateMatcher.SourceLuminance? = null

        val matchesByCrop = mutableMapOf<ResourcePath, CropMatches>()

        for ((resourcePath, digest) in resourceListing.digests) {
            val cropGrid = cropGrid(
                ResourceLocation(documentPath, resourcePath), digest)

            val exactMatches = TemplateMatcher.locate(
                screenshotGrid, cropGrid, limitPerCrop, sourceHistogram)

            val cropMatches =
                if (exactMatches.isNotEmpty() || tolerantThreshold == null) {
                    CropMatches(
                        cropGrid.width,
                        cropGrid.height,
                        exactMatches.map { TemplateMatcher.ScoredMatch(it, 1.0, 1.0) })
                }
                else {
                    val luminance = sourceLuminance
                        ?: TemplateMatcher.SourceLuminance(screenshotGrid).also { sourceLuminance = it }

                    val scored = TemplateMatcher.locateScored(
                        screenshotGrid, cropGrid, tolerantThreshold, luminance)

                    CropMatches(
                        cropGrid.width,
                        cropGrid.height,
                        scored.matches.take(limitPerCrop),
                        if (scored.matches.isEmpty()) { scored.best } else { null })
                }

            matchesByCrop[resourcePath] = cropMatches
        }

        return matchesByCrop
    }


    private suspend fun cropGrid(
        resourceLocation: ResourceLocation,
        digest: Digest
    ): RgbGrid {
        synchronized(cropGridCache) {
            cropGridCache[digest]
        }?.let { return it }

        // Outside the lock (readResource suspends); a racing duplicate decode is harmless
        val cropPngBytes = notationMedia.readResource(resourceLocation)
        val cropImage = ImageIO.read(cropPngBytes.toInputStream())
        val cropGrid = RgbGrid.ofImage(cropImage)

        synchronized(cropGridCache) {
            cropGridCache[digest] = cropGrid
        }

        return cropGrid
    }


    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Device-px to CSS-px factor for mapping screenshot coordinates to `elementFromPoint`.
     */
    private fun screenshotCssScale(
        screenshotWidth: Int,
        driver: RemoteWebDriver
    ): Double {
        val devicePixelRatio = (driver.executeScript(
            "return window.devicePixelRatio") as Number).toDouble()

        val viewportCssWidth = (driver.executeScript(
            "return window.innerWidth") as Number).toDouble()

        // Exotic drivers can screenshot at a scale other than the viewport DPR; the observed
        // width ratio is then the honest device-px to CSS-px factor.
        val dprConsistent =
            screenshotWidth == (viewportCssWidth * devicePixelRatio).roundToInt()

        return if (dprConsistent) {
            1.0 / devicePixelRatio
        }
        else {
            viewportCssWidth / screenshotWidth
        }
    }


    private fun elementAt(
        rectangle: Rectangle,
        cssScale: Double,
        driver: RemoteWebDriver
    ): WebElement? {
        val cssX = rectangle.centerX * cssScale
        val cssY = rectangle.centerY * cssScale

        val element = driver.executeScript(
            "return document.elementFromPoint(arguments[0], arguments[1])", cssX, cssY)

        return element as? WebElement
    }


    private fun isTargetPreview(
        element: WebElement,
        driver: RemoteWebDriver
    ): Boolean {
        return driver.executeScript(
            "return arguments[0].closest('[${TargetDocument.previewDataAttribute}]') != null",
            element
        ) == true
    }
}
