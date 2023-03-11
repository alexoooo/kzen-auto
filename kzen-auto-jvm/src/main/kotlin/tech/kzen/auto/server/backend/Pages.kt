package tech.kzen.auto.server.backend

import kotlinx.html.*
import tech.kzen.auto.common.api.rootHtmlElementId
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.server.jsResourcePath


//object Pages


//---------------------------------------------------------------------------------------------------------------------
fun HTML.indexPage() {
    head {
        title("Kzen Auto")
        meta {
            charset = "UTF-8"
        }

        link("$staticResourcePath/logo.png", "icon", "image/png")
        link("$staticResourcePath/style.css", "stylesheet", "text/css")
        link("$staticResourcePath/normalize.css", "stylesheet", "text/css")

        // see: https://www.npmjs.com/package/react-cropper#installation
        link("$staticResourcePath/cropper.css", "stylesheet", "text/css")

        script("text/javascript", jsResourcePath) {
            defer = true
        }
    }

    body {
        style = "background-color: rgb(225, 225, 225)"

        div {
            id = rootHtmlElementId

            div("fade-in") {
                style = "width: 100%; height: 100%"
                div {
                    style = "width:100px; height:200px; position:absolute; left:0; right:0; top:0; bottom:0; margin:auto"

                    div {
                        img("logo", "$staticResourcePath/logo.png") {
                            height = "100"
                            style = "margin: 0 auto"
                        }
                    }

                    div {
                        style = "width: 10em; margin: 0 auto"

                        div {
                            style = "margin-left: 0.9em"
                            +"Loading..."
                        }

                        div {
                            style = "width: 58%; margin: 0 auto"
                            div("rootLoader")
                        }
                    }
                }
            }
        }
    }
}