package tech.kzen.auto.server.backend

import kotlinx.html.*
import tech.kzen.auto.common.api.rootHtmlElementId
import tech.kzen.auto.common.api.staticResourcePath
import tech.kzen.auto.server.context.KzenAutoConfig


//---------------------------------------------------------------------------------------------------------------------
fun HTML.indexPage(
    kzenAutoConfig: KzenAutoConfig
) {
    head {
        // react-scan: dev-only, accurate live re-render overlay. Replaces reliance on the React
        // DevTools "Highlight updates" overlay, which flashes fibers merely visited during
        // reconciliation (self-duration 0) and so buries true re-renders in false positives.
        // Non-deferred and emitted first, so it patches React before the deferred app bundle's
        // createRoot. Gated to dev (developmentMode) so it never loads in production.
        if (kzenAutoConfig.developmentMode()) {
            script("text/javascript", "https://unpkg.com/react-scan@0.5.7/dist/auto.global.js") {
                attributes["crossorigin"] = "anonymous"
            }
        }

        title("Kzen")
        meta {
            charset = "UTF-8"
        }

        // Version + build timestamp of the running server, read by the client to show as logo hover
        //  text (see HeaderController.renderLogo). Empty when no build stamp is present (dev run).
        meta {
            name = "kzen-build"
            content = kzenAutoConfig.buildInfo?.display() ?: ""
        }

        link("$staticResourcePath/favicon.png", "icon", "image/png")
        link("$staticResourcePath/style.css", "stylesheet", "text/css")
        link("$staticResourcePath/normalize.css", "stylesheet", "text/css")

        // see: https://www.npmjs.com/package/react-cropper#installation
        link("$staticResourcePath/cropper.css", "stylesheet", "text/css")

        script("text/javascript", kzenAutoConfig.jsResourcePath()) {
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