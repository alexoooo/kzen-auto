package tech.kzen.auto.client.util

import org.w3c.dom.events.KeyboardEvent


object ClientInputUtils {
    private const val enterKey = "Enter"
    private const val escapeKey = "Escape"


    fun handleEnter(
        event: KeyboardEvent,
        enterHandler: () -> Unit
    ) {
        if (event.key == enterKey) {
            enterHandler()
            event.preventDefault()
        }
    }


    fun handleEscape(
        event: KeyboardEvent,
        escapeHandler: () -> Unit
    ) {
        if (event.key == escapeKey) {
            escapeHandler()
            event.preventDefault()
        }
    }


    fun handleEnterAndEscape(
        event: KeyboardEvent,
        enterHandler: () -> Unit,
        escapeHandler: () -> Unit
    ) {
        handleEnter(event, enterHandler)
        handleEscape(event, escapeHandler)
    }
}