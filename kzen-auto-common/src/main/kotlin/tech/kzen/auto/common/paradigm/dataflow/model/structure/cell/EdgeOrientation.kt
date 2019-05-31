package tech.kzen.auto.common.paradigm.dataflow.model.structure.cell


enum class EdgeOrientation {
    TopToBottom,
    TopToBottomAndRight,
    TopToRight,
    LeftToBottomAndRight,
    LeftToBottom;


    fun hasTop(): Boolean {
        return when (this) {
            TopToBottom ->
                true

            TopToBottomAndRight ->
                true

            TopToRight ->
                true

            else ->
                false
        }
    }


    fun hasBottom(): Boolean {
        return when (this) {
            TopToBottom ->
                true

            TopToBottomAndRight ->
                true

            LeftToBottomAndRight ->
                true

            LeftToBottom ->
                true

            else ->
                false
        }
    }


    fun hasLeftIngress(): Boolean {
        return when (this) {
            LeftToBottomAndRight ->
                true

            LeftToBottom ->
                true

            else ->
                false
        }
    }


    fun hasRightEgress(): Boolean {
        return when (this) {
            TopToBottomAndRight ->
                true

            TopToRight ->
                true

            LeftToBottomAndRight ->
                true

            else ->
                false
        }
    }


}
