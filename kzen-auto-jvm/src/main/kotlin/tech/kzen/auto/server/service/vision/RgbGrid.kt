package tech.kzen.auto.server.service.vision

import java.awt.image.BufferedImage


data class RgbGrid(
        val width: Int,
        val height: Int,
        private val values: Array<IntArray>
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        fun ofImage(image: BufferedImage): RgbGrid {
            val values = Array(image.width) {
                IntArray(image.height)
            }

            for (x in 0 until image.width) {
                for (y in 0 until image.height) {
                    values[x][y] = image.getRGB(x, y)
                }
            }

            return RgbGrid(
                    image.width,
                    image.height,
                    values)
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    fun get(x: Int, y: Int): Int {
        return values[x][y]
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RgbGrid

        if (width != other.width) return false
        if (height != other.height) return false
        if (!values.contentDeepEquals(other.values)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + values.contentDeepHashCode()
        return result
    }
}