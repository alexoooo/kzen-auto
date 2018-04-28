package tech.kzen.auto.client

import kotlinx.html.dom.create
import kotlinx.html.js.div
import kotlinx.html.p
import org.w3c.dom.get
import tech.kzen.lib.common.getAnswer
import kotlin.browser.document


fun main(args: Array<String>) {
    val message = document.create.div {
        p {
            +"Fooo: ${getAnswer()}"
        }
    }

    val body = document.getElementsByTagName("body")[0]!!
    body.appendChild(message)
}
