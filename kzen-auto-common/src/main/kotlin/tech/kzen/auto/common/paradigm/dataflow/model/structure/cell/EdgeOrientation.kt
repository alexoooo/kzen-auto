package tech.kzen.auto.common.paradigm.dataflow.model.structure.cell


enum class EdgeOrientation {
    TopToBottom,
    TopToLeft,
    TopToRight,
    TopToLeftAndRight,
    TopToBottomAndLeft,
    TopToBottomAndRight,
    TopToBottomAndLeftAndRight,

    LeftToRight,
    LeftToBottom,
    LeftToRightAndBottom,

    RightToLeft,
    RightToBottom,
    RightToLeftAndBottom;


    fun hasTop(): Boolean {
        return when (this) {
            TopToBottom ->
                true

            TopToBottomAndRight ->
                true

            TopToBottomAndLeft ->
                true

            TopToBottomAndLeftAndRight ->
                true

            TopToLeft ->
                true

            TopToRight ->
                true

            TopToLeftAndRight ->
                true

            else ->
                false
        }
    }


    fun hasBottom(): Boolean {
        return when (this) {
            TopToBottom ->
                true

            TopToBottomAndLeft ->
                true

            TopToBottomAndRight ->
                true

            TopToBottomAndLeftAndRight ->
                true

            LeftToRightAndBottom ->
                true

            LeftToBottom ->
                true

            RightToBottom ->
                true

            RightToLeftAndBottom ->
                true

            else ->
                false
        }
    }


    fun hasLeftIngress(): Boolean {
        return when (this) {
            LeftToRight ->
                true

            LeftToBottom ->
                true

            LeftToRightAndBottom ->
                true

            else ->
                false
        }
    }


    fun hasLeftEgress(): Boolean {
        return when (this) {
            TopToBottomAndLeft ->
                true

            TopToBottomAndLeftAndRight ->
                true

            TopToLeft ->
                true

            TopToLeftAndRight ->
                true

            RightToLeft ->
                true

            RightToLeftAndBottom ->
                true

            else ->
                false
        }
    }


    fun hasRightIngress(): Boolean {
        return when (this) {
            RightToLeft ->
                true

            RightToBottom ->
                true

            RightToLeftAndBottom ->
                true

            else ->
                false
        }
    }


    fun hasRightEgress(): Boolean {
        return when (this) {
            TopToBottomAndRight ->
                true

            TopToBottomAndLeftAndRight ->
                true

            TopToRight ->
                true

            TopToLeftAndRight ->
                true

            LeftToRightAndBottom ->
                true

            LeftToRight ->
                true

            else ->
                false
        }
    }
}
