package tech.kzen.auto.server.service.vision

import java.awt.image.BufferedImage


class RgbGrid(
    val width: Int,
    val height: Int,
    private val values: IntArray
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofImage(image: BufferedImage): RgbGrid {
            val values = image.getRGB(
                0, 0, image.width, image.height, null, 0, image.width)

            return RgbGrid(
                image.width,
                image.height,
                values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(x: Int, y: Int): Int {
        return values[y * width + x]
    }
}
