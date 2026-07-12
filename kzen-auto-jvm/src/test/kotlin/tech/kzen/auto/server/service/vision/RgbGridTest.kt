package tech.kzen.auto.server.service.vision

import org.junit.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals


class RgbGridTest {
    //-----------------------------------------------------------------------------------------------------------------
    /**
     * Pins x/y axis order and full-ARGB semantics: get(x, y) must agree with
     * BufferedImage.getRGB(x, y), including the alpha channel.
     */
    @Test
    fun ofImageMatchesPerPixelGetRgb() {
        val width = 7
        val height = 5
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (x * 37 + y * 11) % 256
                val rgb = (x * 41 + y * 101 * 256 + x * y * 65536) % 0x1000000
                image.setRGB(x, y, (alpha shl 24) or rgb)
            }
        }

        val grid = RgbGrid.ofImage(image)

        assertEquals(width, grid.width)
        assertEquals(height, grid.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                assertEquals(image.getRGB(x, y), grid.get(x, y), "at ($x, $y)")
            }
        }
    }
}
