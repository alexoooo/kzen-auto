package tech.kzen.auto.client.util


object ClientInputUtils {
    @Suppress("ConstPropertyName")
    private const val enterKey = "Enter"

    @Suppress("ConstPropertyName")
    private const val escapeKey = "Escape"


    fun handleEnter(
        event: react.dom.events.KeyboardEvent<*>,
        enterHandler: () -> Unit
    ) {
        if (event.key == enterKey) {
            enterHandler()
            event.preventDefault()
        }
    }


//    fun handleEnter(
//        event: org.w3c.dom.events.KeyboardEvent,
//        enterHandler: () -> Unit
//    ) {
//        if (event.key == enterKey) {
//            enterHandler()
//            event.preventDefault()
//        }
//    }


//    fun handleEnter(
//        event: web.uievents.KeyboardEvent,
//        enterHandler: () -> Unit
//    ) {
//        if (event.key == enterKey) {
//            enterHandler()
//            event.preventDefault()
//        }
//    }


//    fun handleEscape(
//        event: org.w3c.dom.events.KeyboardEvent,
//        escapeHandler: () -> Unit
//    ) {
//        if (event.key == escapeKey) {
//            escapeHandler()
//            event.preventDefault()
//        }
//    }


    fun handleEscape(
        event: react.dom.events.KeyboardEvent<*>,
        escapeHandler: () -> Unit
    ) {
        if (event.key == escapeKey) {
            escapeHandler()
            event.preventDefault()
        }
    }


//    fun handleEscape(
//        event: web.uievents.KeyboardEvent,
//        escapeHandler: () -> Unit
//    ) {
//        if (event.key == escapeKey) {
//            escapeHandler()
//            event.preventDefault()
//        }
//    }


//    fun handleEnterAndEscape(
//        event: org.w3c.dom.events.KeyboardEvent,
//        enterHandler: () -> Unit,
//        escapeHandler: () -> Unit
//    ) {
//        handleEnter(event, enterHandler)
//        handleEscape(event, escapeHandler)
//    }


    fun handleEnterAndEscape(
        event: react.dom.events.KeyboardEvent<*>,
        enterHandler: () -> Unit,
        escapeHandler: () -> Unit
    ) {
        handleEnter(event, enterHandler)
        handleEscape(event, escapeHandler)
    }


//    fun handleEnterAndEscape(
//        event: web.uievents.KeyboardEvent,
//        enterHandler: () -> Unit,
//        escapeHandler: () -> Unit
//    ) {
//        handleEnter(event, enterHandler)
//        handleEscape(event, escapeHandler)
//    }
}