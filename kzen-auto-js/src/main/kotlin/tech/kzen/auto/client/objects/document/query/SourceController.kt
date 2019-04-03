package tech.kzen.auto.client.objects.document.query

import kotlinx.css.Color
import org.w3c.dom.HTMLInputElement
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.h3
import styled.css
import styled.styledDiv
import tech.kzen.auto.client.wrap.MaterialTextField
import tech.kzen.lib.common.model.attribute.AttributeNesting
import tech.kzen.lib.common.model.attribute.AttributePath
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.structure.GraphStructure


class SourceController(
        props: SourceController.Props
):
        RComponent<SourceController.Props, SourceController.State>(props)
{
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val filePathAttribute = AttributePath.parse("filePath")
    }


    //-----------------------------------------------------------------------------------------------------------------
    class Props(
            var attributeNesting: AttributeNesting,
            var objectLocation: ObjectLocation,
            var graphStructure: GraphStructure
    ): RProps


    class State(
//            var hoverCard: Boolean,
//            var hoverMenu: Boolean,
//            var intentToRun: Boolean,
//
//            var optionsOpen: Boolean
    ): RState


    //-----------------------------------------------------------------------------------------------------------------
    override fun RBuilder.render() {
        val filePath = props
                .graphStructure
                .graphNotation
                .transitiveAttribute(props.objectLocation, filePathAttribute)
                ?.asString()
                ?: return

        styledDiv {
            key = props.objectLocation.toReference().asString()

            css {
                backgroundColor = Color.white
            }

            h3 {
                +"CSV source"
            }

            child(MaterialTextField::class) {
                attrs {
                    fullWidth = true

                    label = "File Path"
                    value = filePath

                    onChange = {
                        val target = it.target as HTMLInputElement
//                        onValueChange(target.value)
                    }
                }
            }
        }
    }
}