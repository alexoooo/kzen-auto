package tech.kzen.auto.common.paradigm.dataflow.model.structure.cell


enum class EdgeDirection(
        val rowOffset: Int,
        val columnOffset: Int
) {
    Top(-1, 0),
    Right(0, 1),
    Bottom(1, 0),
    Left(0, -1);


    fun reverse(): EdgeDirection {
        return when (this) {
            Top ->
                Bottom

            Right ->
                Left

            Bottom ->
                Top

            Left ->
                Right
        }
    }
}