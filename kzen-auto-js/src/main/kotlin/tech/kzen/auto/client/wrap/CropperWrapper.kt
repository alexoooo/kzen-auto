package tech.kzen.auto.client.wrap

import kotlinx.css.*
import org.w3c.dom.HTMLImageElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import styled.css
import styled.styledDiv
import styled.styledImg
import kotlin.js.json


class CropperWrapper:
        RComponent<RProps, RState>()
{
    //-----------------------------------------------------------------------------------------------------------------
    private var imageElement: HTMLImageElement? = null
    private var cropper: Cropper? = null


    //-----------------------------------------------------------------------------------------------------------------
    override fun componentDidMount() {
//        console.log("cropperjs", cropperjs)

        val options = json()

//        options["aspectRatio"] = Double.NaN
        options["autoCropArea"] = 0.05

//        async {
//            delay(1000)
            cropper = Cropper(imageElement!!, options)
//        }
    }


    override fun componentWillUnmount() {
        cropper?.destroy()
        cropper = null
        imageElement = null
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        styledDiv {
            css {
//                position: relative; width: 100%; max-height: 497px; min-height: 200px;
                position = Position.relative
                width = 100.pct
                height = 100.pct
//                maxHeight = 300.px
//                minHeight = 200.px
            }

            styledImg {
                css {
                    // opacity = 0
                    maxWidth = 100.pct
                    maxHeight = 100.pct
                }

                attrs {
                    src = "screenshot.png"
                }

                ref {
                    imageElement = it as? HTMLImageElement
                }
            }
        }
    }
}