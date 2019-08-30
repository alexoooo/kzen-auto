package tech.kzen.auto.client.objects.document.feature

import kotlinx.css.*
import react.*
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.objects.document.DocumentController
import tech.kzen.auto.client.wrap.CropperWrapper
import tech.kzen.auto.client.wrap.RPureComponent
import tech.kzen.auto.common.objects.document.DocumentArchetype


class FeatureController:
        RPureComponent<RProps, RState>()
{
    //-----------------------------------------------------------------------------------------------------------------
    @Suppress("unused")
    class Wrapper(
            private val type: DocumentArchetype
    ):
            DocumentController
    {
        override fun type(): DocumentArchetype {
            return type
        }

        override fun child(input: RBuilder, handler: RHandler<RProps>): ReactElement {
            return input.child(FeatureController::class) {
//                attrs {
//                    this.attributeController = this@Wrapper.attributeController
//                }

                handler()
            }
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        +"ddd"

        styledDiv {
            css {
                width = 100.pct.minus(2.em)
//                height = 600.px
                height = 100.vh.minus(8.em)
                minHeight = 200.px
                maxHeight = 1024.px
                padding(1.em)
//                overflow = Overflow.hidden
            }
            child(CropperWrapper::class) {}
        }
    }
}