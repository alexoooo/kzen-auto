package tech.kzen.auto.common.objects.document.report.spec.input

import tech.kzen.auto.common.objects.document.plugin.model.CommonPluginCoordinate
import tech.kzen.auto.common.objects.document.report.ReportConventions
import tech.kzen.auto.common.util.data.DataLocation
import tech.kzen.lib.common.api.AttributeDefiner
import tech.kzen.lib.common.model.attribute.AttributeName
import tech.kzen.lib.common.model.attribute.AttributeSegment
import tech.kzen.lib.common.model.definition.AttributeDefinitionAttempt
import tech.kzen.lib.common.model.definition.GraphDefinition
import tech.kzen.lib.common.model.definition.ValueAttributeDefinition
import tech.kzen.lib.common.model.instance.GraphInstance
import tech.kzen.lib.common.model.locate.ObjectLocation
import tech.kzen.lib.common.model.structure.GraphStructure
import tech.kzen.lib.common.model.structure.notation.MapAttributeNotation
import tech.kzen.lib.common.model.structure.notation.PositionRelation
import tech.kzen.lib.common.model.structure.notation.ScalarAttributeNotation
import tech.kzen.lib.common.model.structure.notation.cqrs.*
import tech.kzen.lib.common.reflect.Reflect
import tech.kzen.lib.platform.ClassName


data class InputSpec(
    val browser: InputBrowserSpec,
    val selection: InputSelectionSpec
) {
    //-----------------------------------------------------------------------------------------------------------------
    companion object {
        private val browserKey = AttributeSegment.ofKey("browser")
        val browserAttributePath = ReportConventions.inputAttributePath.nest(browserKey)

        private val selectionKey = AttributeSegment.ofKey("selection")
        val selectionAttributePath = ReportConventions.inputAttributePath.nest(selectionKey)


        fun browseCommand(mainLocation: ObjectLocation, directory: DataLocation): UpdateInAttributeCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                InputBrowserSpec.directoryAttributePath,
                ScalarAttributeNotation(directory.asString()))
        }


        fun filterCommand(mainLocation: ObjectLocation, filter: String): UpdateInAttributeCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                InputBrowserSpec.filterAttributePath,
                ScalarAttributeNotation(filter))
        }


        fun addSelectedCommand(
            mainLocation: ObjectLocation,
            paths: List<InputDataSpec>
        ): InsertAllListItemsInAttributeCommand {
            return InsertAllListItemsInAttributeCommand(
                mainLocation,
                InputSelectionSpec.locationsAttributePath,
                PositionRelation.afterLast,
                paths.map { it.asNotation() }
            )
        }


        fun removeSelectedCommand(
            mainLocation: ObjectLocation,
            paths: List<InputDataSpec>
        ): RemoveAllListItemsInAttributeCommand {
            return RemoveAllListItemsInAttributeCommand(
                mainLocation,
                InputSelectionSpec.locationsAttributePath,
                paths.map { it.asNotation() },
                false
            )
        }


        fun selectDataTypeCommand(
            mainLocation: ObjectLocation,
            dataType: ClassName
        ): UpdateInAttributeCommand {
            return UpdateInAttributeCommand(
                mainLocation,
                InputSelectionSpec.dataTypeAttributePath,
                ScalarAttributeNotation(dataType.asString())
            )
        }


        fun selectFormatCommand(
            mainLocation: ObjectLocation,
            inputSelectionSpec: InputSelectionSpec,
            dataLocations: List<DataLocation>,
            pluginCoordinate: CommonPluginCoordinate
        ): UpdateAllNestingsInAttributeCommand {
            val specDataLocations = inputSelectionSpec.locations.map { it.location }
            val dataLocationIndexes = dataLocations.map { specDataLocations.indexOf(it) }
            val dataLocationCoordinateNestings = dataLocationIndexes.map { InputDataSpec.coordinateNesting(it) }

            return UpdateAllNestingsInAttributeCommand(
                mainLocation,
                ReportConventions.inputAttributeName,
                dataLocationCoordinateNestings,
                ScalarAttributeNotation(pluginCoordinate.asString())
            )
        }


        fun selectMultiFormatCommand(
            mainLocation: ObjectLocation,
            inputSelectionSpec: InputSelectionSpec,
            locationFormats: Map<DataLocation, CommonPluginCoordinate>
        ): UpdateAllValuesInAttributeCommand {
            val specDataLocations = inputSelectionSpec.locations.map { it.location }

            val nestingNotations = locationFormats.map {
                InputDataSpec.coordinateNesting(specDataLocations.indexOf(it.key)) to
                        ScalarAttributeNotation(it.value.asString())
            }.toMap()

            return UpdateAllValuesInAttributeCommand(
                mainLocation,
                ReportConventions.inputAttributeName,
                nestingNotations
            )
        }
    }


    //-----------------------------------------------------------------------------------------------------------------
    @Reflect
    object Definer: AttributeDefiner {
        override fun define(
            objectLocation: ObjectLocation,
            attributeName: AttributeName,
            graphStructure: GraphStructure,
            partialGraphDefinition: GraphDefinition,
            partialGraphInstance: GraphInstance
        ): AttributeDefinitionAttempt {
            check(attributeName == ReportConventions.inputAttributeName) {
                "Unexpected attribute name: $attributeName"
            }

            val attributeNotation = graphStructure
                .graphNotation
                .mergeAttribute(objectLocation, ReportConventions.inputAttributeName) as? MapAttributeNotation
                ?: return AttributeDefinitionAttempt.failure(
                    "'${ReportConventions.inputAttributeName}' attribute notation not found:" +
                            " $objectLocation - $attributeName")

            val browser = InputBrowserSpec.ofNotation(attributeNotation.get(browserKey) as MapAttributeNotation)

            val selectionNotation = attributeNotation.get(selectionKey) as MapAttributeNotation
            val selection = InputSelectionSpec.ofNotation(selectionNotation)
            val inputSpec = InputSpec(browser, selection)

            return AttributeDefinitionAttempt.success(
                ValueAttributeDefinition(inputSpec))
        }
    }
}